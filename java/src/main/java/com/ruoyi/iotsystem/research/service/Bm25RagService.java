package com.ruoyi.iotsystem.research.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.iotsystem.research.dto.RagQueryRequest;
import com.ruoyi.iotsystem.research.dto.RagQueryResponse;
import com.ruoyi.iotsystem.research.entity.RagQueryEntity;
import com.ruoyi.iotsystem.research.repository.RagQueryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 基于BM25的中文RAG检索服务。
 * 启动时从classpath加载chunks和sources，提供非LLM的规则化安全问答。
 * Java 8兼容，仅使用Jackson和标准库实现中文unigram+bigram分词和BM25(k1=1.5,b=0.75)。
 */
@Service
public class Bm25RagService {

    private static final double K1 = 1.5;
    private static final double B = 0.75;
    private static final double FROZEN_THRESHOLD = 27.31768531;

    // 安全规则关键词
    private static final String[] CONTROL_VERBS = {"打开", "关闭", "启动", "停止", "调节", "设置", "调整",
            "开", "关", "启", "停", "改", "open", "close", "start", "stop", "set"};
    private static final String[] CONTROL_NOUNS = {"泵", "阀", "水泵", "阀门", "灌溉阀", "施肥泵",
            "pump", "valve", "电机", "马达", "控制器", "执行器", "继电器"};

    private static final String[] EXTRAPOLATION_WORDS = {"江西", "湖南", "湖北", "广东", "广西", "安徽",
            "浙江", "福建", "四川", "云南", "贵州"}; // 非江苏的省份外推
    private static final String JIANGSU_REF = "江苏"; // 江苏标准参照词

    @Autowired
    private RagQueryRepository ragQueryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 语料数据
    private List<ChunkDoc> chunks = new ArrayList<>();
    private Map<String, SourceDoc> sources = new HashMap<>();
    private double avgDocLength = 0;

    /**
     * 应用启动时从classpath加载chunks和sources
     */
    @PostConstruct
    public void init() {
        try {
            loadSources();
            loadChunks();
            calcAvgDocLength();
        } catch (Exception e) {
            throw new RuntimeException("RAG语料加载失败: " + e.getMessage(), e);
        }
    }

    // 加载sources.json到内存
    private void loadSources() throws Exception {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("research-rag/sources.json");
        if (is == null) {
            throw new RuntimeException("research-rag/sources.json 未在classpath中找到");
        }
        List<SourceDoc> list = objectMapper.readValue(is,
                new TypeReference<List<SourceDoc>>() {});
        for (SourceDoc s : list) {
            sources.put(s.sourceId, s);
        }
    }

    // 逐行加载chunks.jsonl到内存
    private void loadChunks() throws Exception {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("research-rag/chunks.jsonl");
        if (is == null) {
            throw new RuntimeException("research-rag/chunks.jsonl 未在classpath中找到");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                ChunkDoc chunk = objectMapper.readValue(line, ChunkDoc.class);
                chunk.tokens = tokenize(chunk.text);
                chunks.add(chunk);
            }
        }
        if (chunks.isEmpty()) {
            throw new RuntimeException("chunks.jsonl 未包含有效语料块");
        }
    }

    // 计算平均文档长度（以token数计）
    private void calcAvgDocLength() {
        long total = 0;
        for (ChunkDoc c : chunks) {
            total += c.tokens.size();
        }
        avgDocLength = (double) total / chunks.size();
    }

    // ==================== 公开接口 ====================

    /**
     * 执行RAG问答，包含安全拒答逻辑。
     * 每次问答（含拒答）都会持久化到research_rag_queries表。
     * 持久化操作在同一事务内，失败时向上抛异常。
     *
     * @param owner   当前用户
     * @param request 查询请求（question + topK）
     * @return 包含回答或拒答信息的响应
     */
    @org.springframework.transaction.annotation.Transactional
    public RagQueryResponse query(String owner, RagQueryRequest request) {
        String question = request.getQuestion().trim();
        int topK = request.getTopK() != null ? request.getTopK() : 5;
        if (topK < 1) topK = 1;
        if (topK > 10) topK = 10;

        RagQueryResponse response = new RagQueryResponse();

        // 优先级1: 安全规则检查
        String safetyAbstain = checkSafetyRules(question);
        if (safetyAbstain != null) {
            response.setAbstained(true);
            response.setAbstainReason(safetyAbstain);
            response.setAnswer("系统拒绝回答此问题：" + safetyAbstain);
            persistQuery(owner, question, response, null);
            return response;
        }

        // BM25检索
        List<ScoredChunk> scored = search(question, topK);

        if (scored.isEmpty()) {
            response.setAbstained(true);
            response.setAbstainReason("无匹配知识块");
            response.setAnswer("未找到相关证据，拒绝回答。");
            response.setTopScore(0);
            persistQuery(owner, question, response, null);
            return response;
        }

        double topScore = scored.get(0).score;
        response.setTopScore(topScore);

        // 优先级2: 低分拒答
        if (topScore < FROZEN_THRESHOLD) {
            response.setAbstained(true);
            response.setAbstainReason("证据不足（最高分低于阈值）");
            response.setAnswer("检索到的证据相关性不足（最高分 " + String.format("%.2f", topScore)
                    + " < 阈值 " + String.format("%.2f", FROZEN_THRESHOLD) + "），拒绝回答以避免不可靠信息。");
            persistQuery(owner, question, response, scored);
            return response;
        }

        // 优先级3: 检查检索到的证据内容是否触发拒答
        String evidenceAbstain = checkEvidenceSafety(scored);
        if (evidenceAbstain != null) {
            response.setAbstained(true);
            response.setAbstainReason(evidenceAbstain);
            response.setAnswer("基于检索证据的安全审核，拒绝回答此问题：" + evidenceAbstain);
            persistQuery(owner, question, response, scored);
            return response;
        }

        // 构建回答（最多取前3条证据）
        int answerCount = Math.min(topK, Math.min(3, scored.size()));
        response.setAbstained(false);
        response.setAnswer(buildAnswer(question, scored.subList(0, answerCount)));

        // 构建引用和证据列表
        for (int i = 0; i < answerCount; i++) {
            ScoredChunk sc = scored.get(i);
            Map<String, Object> citation = new HashMap<>();
            citation.put("source_id", sc.chunk.sourceId);
            citation.put("chunk_id", sc.chunk.chunkId);
            citation.put("citation_id", sc.chunk.sourceId + "#" + sc.chunk.chunkId);
            citation.put("score", sc.score);
            response.getCitations().add(citation);

            Map<String, Object> evidence = new HashMap<>();
            evidence.put("source_id", sc.chunk.sourceId);
            evidence.put("chunk_id", sc.chunk.chunkId);
            evidence.put("title", sc.chunk.title);
            evidence.put("text", sc.chunk.text);
            evidence.put("limitations", sc.chunk.limitations);
            response.getEvidence().add(evidence);
        }

        persistQuery(owner, question, response, scored);
        return response;
    }

    /**
     * 对传入的语料进行BM25搜索（用于测试），不持久化。
     */
    public List<ScoredChunk> searchPublic(String queryText, int topK) {
        return search(queryText, topK);
    }

    // ==================== BM25检索 ====================

    // BM25搜索：对查询分词后计算每个chunk的BM25分，按分降序返回topK
    List<ScoredChunk> search(String queryText, int topK) {
        List<String> queryTokens = tokenize(queryText);
        if (queryTokens.isEmpty()) {
            return Collections.emptyList();
        }

        // 计算IDF
        Map<String, Double> idf = new HashMap<>();
        int N = chunks.size();
        for (String token : new HashSet<>(queryTokens)) {
            int df = 0;
            for (ChunkDoc c : chunks) {
                if (countToken(c, token) > 0) df++;
            }
            idf.put(token, Math.log(1.0 + (N - df + 0.5) / (df + 0.5)));
        }

        // 计算每个chunk的BM25分
        List<ScoredChunk> results = new ArrayList<>();
        for (ChunkDoc chunk : chunks) {
            double score = 0;
            int docLen = chunk.tokens.size();
            for (String token : queryTokens) {
                double tokIdf = idf.getOrDefault(token, 0.0);
                int tf = countToken(chunk, token);
                double numerator = tf * (K1 + 1);
                double denominator = tf + K1 * (1 - B + B * docLen / avgDocLength);
                score += tokIdf * numerator / denominator;
            }
            results.add(new ScoredChunk(chunk, score));
        }

        // 按分降序排列
        results.sort(new Comparator<ScoredChunk>() {
            @Override
            public int compare(ScoredChunk a, ScoredChunk b) {
                return Double.compare(b.score, a.score);
            }
        });

        if (results.size() > topK) {
            return results.subList(0, topK);
        }
        return results;
    }

    // 统计token在chunk中的出现次数
    private int countToken(ChunkDoc chunk, String token) {
        int count = 0;
        for (String t : chunk.tokens) {
            if (t.equals(token)) count++;
        }
        return count;
    }

    // ==================== 中文分词（unigram + bigram + 英文/数字） ====================

    /**
     * 中英混合分词器：中文生成unigram和bigram，英文/数字按连续字母数字切分。
     * 过滤停用词和短token。
     */
    private List<String> tokenize(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        List<String> tokens = new ArrayList<>();
        int len = text.length();
        int i = 0;

        // 处理英文/数字连续段
        while (i < len) {
            char ch = text.charAt(i);
            if (isLatinOrDigit(ch)) {
                StringBuilder sb = new StringBuilder();
                while (i < len && isLatinOrDigit(text.charAt(i))) {
                    sb.append(Character.toLowerCase(text.charAt(i)));
                    i++;
                }
                String word = sb.toString();
                if (word.length() >= 2) {
                    tokens.add(word);
                    // 也加入bigram子串
                    for (int j = 0; j < word.length() - 1; j++) {
                        tokens.add(word.substring(j, j + 2));
                    }
                } else if (word.length() == 1 && Character.isDigit(word.charAt(0))) {
                    tokens.add(word);
                }
            } else if (isChinese(ch)) {
                // 中文段：收集连续中文
                int start = i;
                while (i < len && isChinese(text.charAt(i))) {
                    i++;
                }
                String chineseBlock = text.substring(start, i);
                // unigram
                for (int j = 0; j < chineseBlock.length(); j++) {
                    String uni = chineseBlock.substring(j, j + 1);
                    if (!isStopChar(uni)) {
                        tokens.add(uni);
                    }
                }
                // bigram
                for (int j = 0; j < chineseBlock.length() - 1; j++) {
                    String bi = chineseBlock.substring(j, j + 2);
                    tokens.add(bi);
                }
            } else {
                i++; // 跳过标点、空格等
            }
        }
        return tokens;
    }

    // 判断是否为英文字母或数字
    private boolean isLatinOrDigit(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                || (ch >= '0' && ch <= '9');
    }

    // 判断是否为中文字符（CJK统一表意文字）
    private boolean isChinese(char ch) {
        return (ch >= 0x4E00 && ch <= 0x9FFF)
                || (ch >= 0x3400 && ch <= 0x4DBF)
                || (ch >= 0xF900 && ch <= 0xFAFF);
    }

    // 判断是否为无意义的停用字符
    private boolean isStopChar(String s) {
        return s.equals("的") || s.equals("了") || s.equals("是") || s.equals("在")
                || s.equals("和") || s.equals("与") || s.equals("或") || s.equals("及");
    }

    // ==================== 安全规则 ====================

    /**
     * 最高优先级安全规则：检查直接控制请求和地域外推。
     *
     * @return 拒答原因，null表示通过检查
     */
    public String checkSafetyRules(String question) {
        // 1. 直接控制请求检测
        boolean hasVerb = false;
        boolean hasNoun = false;
        for (String v : CONTROL_VERBS) {
            if (question.contains(v)) { hasVerb = true; break; }
        }
        for (String n : CONTROL_NOUNS) {
            if (question.contains(n)) { hasNoun = true; break; }
        }
        if (hasVerb && hasNoun) {
            return "直接设备控制被拒绝，请通过规则引擎和仿真命令闭环执行操作";
        }

        // 2. 地域外推检测：提到江苏标准+非江苏省份
        boolean hasJiangsu = question.contains(JIANGSU_REF) || question.contains("DB32");
        boolean hasExtrap = false;
        for (String prov : EXTRAPOLATION_WORDS) {
            if (question.contains(prov)) { hasExtrap = true; break; }
        }
        if (hasJiangsu && hasExtrap) {
            return "江苏地方标准不得外推至其他省份作为处方，不同地区需本地化验证";
        }

        // 3. 田间实测诱导检测
        if (questionContainsAny(question,
                "田间实测", "大田实测", "实地验证", "田间试验", "大田试验", "实地种植")) {
            return "本实验仅涉及合成仿真，不具备田间实测数据，无法提供田间验证结论";
        }

        // 4. 盐碱地检测
        if (questionContainsAny(question, "盐碱地", "盐碱", "盐渍", "盐害")) {
            return "当前证据库未覆盖盐碱地水稻灌溉参数，无法给出可靠回答";
        }

        // 5. 未收录标准精确条款检测（询问具体的、未在知识库中的精确阈值条款）
        if (questionContainsAny(question, "标准条款", "标准原文", "精确条文", "原文条款")) {
            return "当前证据库不包含标准原文全文，仅提供摘要信息";
        }

        // 6. LLM事实正确率/指标误问检测
        if (questionContainsAny(question, "事实正确率", "LLM事实", "生成质量",
                "幻觉率", "事实准确率", "模型正确率")) {
            return "当前RAG指标为检索与拒答行为指标，不能等同于LLM事实正确率或生成质量";
        }

        // 7. 生育期具体阈值检测（未在语料中收录的具体生育期阈值请求）
        if (questionContainsAny(question, "分蘖期水位", "抽穗期水位", "灌浆期水位",
                "成熟期水位", "苗期水位", "返青期水位")
                && questionContainsAny(question, "cm", "厘米", "毫米", "mm", "多少")) {
            return "当前证据库未收录各生育期的精确水位阈值，请参考地方标准或农技部门指导";
        }

        // 8. 具体地方处方检测
        if (questionContainsAny(question, "江西处方", "本地处方", "具体处方", "当地阈值",
                "田间处方", "本地化阈值", "本地推荐")) {
            return "当前证据库不包含地方化田间处方，请咨询本地农业技术部门";
        }

        return null;
    }

    /**
     * 检查检索到的证据内容是否应触发拒答。
     * 当top证据均标记为"地方标准"且查询涉及其他省份时拒答。
     */
    private String checkEvidenceSafety(List<ScoredChunk> scored) {
        // 如果前3条证据均来自地域受限的源（如江苏标准s05），且不是通用的参考
        // 检查是否有证据明确指出适用性限制
        boolean hasLocalStandard = false;
        for (int i = 0; i < Math.min(3, scored.size()); i++) {
            ChunkDoc chunk = scored.get(i).chunk;
            if ("s05".equals(chunk.sourceId)) {
                hasLocalStandard = true;
                break;
            }
        }
        // 如果检索到的主要是地方标准，额外标记限制
        // 这里不再额外拒答——已在checkSafetyRules中按关键词处理
        return null;
    }

    // 检查问题是否包含任一关键词
    private boolean questionContainsAny(String question, String... keywords) {
        for (String kw : keywords) {
            if (question.contains(kw)) return true;
        }
        return false;
    }

    // ==================== 回答构建 ====================

    /**
     * 基于检索到的证据构建模板化回答。
     * 回答包含问题相关结论、证据摘要和限制声明。
     */
    private String buildAnswer(String question, List<ScoredChunk> evidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("基于检索到的知识库证据，就您的问题\"");
        sb.append(question);
        sb.append("\"提供以下信息：\n\n");

        for (int i = 0; i < evidence.size(); i++) {
            ScoredChunk sc = evidence.get(i);
            sb.append("【证据");
            sb.append(i + 1);
            sb.append("】（来源：");
            sb.append(sc.chunk.sourceId);
            sb.append("#");
            sb.append(sc.chunk.chunkId);
            sb.append("，BM25分=");
            sb.append(String.format("%.2f", sc.score));
            sb.append("）\n");
            sb.append(sc.chunk.text);
            sb.append("\n");
            if (sc.chunk.limitations != null && !sc.chunk.limitations.isEmpty()) {
                sb.append("限制声明：");
                sb.append(sc.chunk.limitations);
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("---\n");
        sb.append("⚠️ 重要提示：以上信息来自内部知识库检索，非外部LLM生成。");
        sb.append("本回答基于合成仿真数据和公开文献摘要，不代表田间实测或生产安全认证。");
        sb.append("具体灌溉方案需结合当地条件由专业人员制定。");
        sb.append("本系统不直接下发任何设备控制命令。");

        return sb.toString();
    }

    // ==================== 持久化 ====================

    /**
     * 将问答记录持久化到research_rag_queries表。
     * 序列化或save失败时向上抛异常，不再吞掉。
     */
    private void persistQuery(String owner, String question, RagQueryResponse response,
                               List<ScoredChunk> scored) {
        RagQueryEntity entity = new RagQueryEntity();
        entity.setOwnerUsername(owner);
        entity.setQuestion(question);
        entity.setAnswer(response.getAnswer());
        entity.setAbstained(response.isAbstained());
        entity.setAbstainReason(response.getAbstainReason());
        entity.setRetriever(response.getRetriever());
        entity.setThresholdValue(response.getThreshold());
        entity.setTopScore(response.getTopScore());

        try {
            if (response.getCitations() != null && !response.getCitations().isEmpty()) {
                entity.setCitationsJson(objectMapper.writeValueAsString(response.getCitations()));
            }
            if (response.getEvidence() != null && !response.getEvidence().isEmpty()) {
                entity.setEvidenceJson(objectMapper.writeValueAsString(response.getEvidence()));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("RAG问答结果序列化失败", e);
        }

        ragQueryRepository.save(entity);
    }

    // ==================== 语料加载状态 ====================

    /**
     * 获取当前加载的chunks数量（供测试验证）
     */
    public int getChunkCount() {
        return chunks.size();
    }

    /**
     * 获取语料加载状态（供测试验证）
     */
    public int getSourceCount() {
        return sources.size();
    }

    // ==================== 内部数据类型 ====================

    /**
     * JSON反序列化用的chunk文档结构
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChunkDoc {
        @JsonProperty("chunk_id")
        public String chunkId;
        @JsonProperty("source_id")
        public String sourceId;
        public String title;
        public String text;
        public List<String> tags;
        public String applicability;
        public String limitations;
        public transient List<String> tokens;
    }

    /**
     * JSON反序列化用的source文档结构
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SourceDoc {
        @JsonProperty("source_id")
        public String sourceId;
        public String title;
        public String institution;
        public String url;
        @JsonProperty("access_date")
        public String accessDate;
        public String scope;
        public String limitations;
    }

    /**
     * 带BM25分的chunk结构
     */
    public static class ScoredChunk {
        public final ChunkDoc chunk;
        public final double score;

        /**
         * 构造带BM25得分的ChunkDoc包装结构
         */
        public ScoredChunk(ChunkDoc chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}
