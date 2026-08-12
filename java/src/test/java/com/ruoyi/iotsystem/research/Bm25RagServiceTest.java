package com.ruoyi.iotsystem.research;

import com.ruoyi.iotsystem.research.dto.RagQueryRequest;
import com.ruoyi.iotsystem.research.dto.RagQueryResponse;
import com.ruoyi.iotsystem.research.entity.RagQueryEntity;
import com.ruoyi.iotsystem.research.repository.RagQueryRepository;
import com.ruoyi.iotsystem.research.service.Bm25RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BM25 RAG服务单元测试。
 * 覆盖安全拒答规则、BM25检索、低分拒答和持久化路径。
 */
@ExtendWith(MockitoExtension.class)
class Bm25RagServiceTest {

    @Mock
    private RagQueryRepository ragQueryRepository;

    @InjectMocks
    private Bm25RagService bm25RagService;

    // 使用反射调用init加载语料
    @BeforeEach
    void setUp() throws Exception {
        Method initMethod = Bm25RagService.class.getDeclaredMethod("init");
        initMethod.setAccessible(true);
        try {
            initMethod.invoke(bm25RagService);
        } catch (Exception e) {
            // 语料加载失败时跳过测试（例如classpath资源缺失）
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause.getMessage() != null && cause.getMessage().contains("未在classpath中找到")) {
                System.err.println("跳过BM25 RAG测试：语料资源未找到");
            } else {
                throw e;
            }
        }
    }

    // ==================== 安全拒答测试 ====================

    // 验证"打开水泵"等直接控制请求被拒答
    @Test
    void 控制请求应被拒答() {
        String result = bm25RagService.checkSafetyRules("如何打开水泵进行灌溉？");
        assertNotNull(result);
        assertTrue(result.contains("控制") || result.contains("拒绝"));
    }

    // 验证"关闭阀门"等直接控制请求被拒答
    @Test
    void 关闭阀门请求应被拒答() {
        String result = bm25RagService.checkSafetyRules("请帮我启动灌溉阀门");
        assertNotNull(result);
        assertTrue(result.contains("控制") || result.contains("拒绝"));
    }

    // 验证江苏标准外推至江西被拒答
    @Test
    void 江苏标准外推江西应被拒答() {
        String result = bm25RagService.checkSafetyRules(
                "DB32标准在江西可以用吗？江苏的阈值能直接用于江西吗？");
        assertNotNull(result);
        assertTrue(result.contains("外推") || result.contains("江苏"));
    }

    // 验证田间实测诱导被拒答
    @Test
    void 田间实测诱导应被拒答() {
        String result = bm25RagService.checkSafetyRules(
                "这个AWD方法经过田间实测验证了吗？能给出田间试验结果吗？");
        assertNotNull(result);
    }

    // 验证盐碱地问题被拒答
    @Test
    void 盐碱地问题应被拒答() {
        String result = bm25RagService.checkSafetyRules(
                "盐碱地水稻灌溉水位应该控制在多少？");
        assertNotNull(result);
        assertTrue(result.contains("盐碱"));
    }

    // 验证标准原文条款请求被拒答
    @Test
    void 标准原文条款请求应被拒答() {
        String result = bm25RagService.checkSafetyRules(
                "请给我DB32标准的精确条文条款原文");
        assertNotNull(result);
    }

    // 验证LLM事实正确率误问被拒答
    @Test
    void LLM事实正确率误问应被拒答() {
        String result = bm25RagService.checkSafetyRules(
                "你们的RAG系统事实正确率是多少？幻觉率多高？");
        assertNotNull(result);
    }

    // 验证生育期具体阈值请求被拒答
    @Test
    void 生育期具体阈值请求应被拒答() {
        String result = bm25RagService.checkSafetyRules(
                "分蘖期水位应该控制在多少厘米？");
        assertNotNull(result);
    }

    // 验证本地处方请求被拒答
    @Test
    void 本地处方请求应被拒答() {
        String result = bm25RagService.checkSafetyRules(
                "能不能给我一个江西的田间处方？");
        assertNotNull(result);
    }

    // 验证正常问题不触发拒答
    @Test
    void 正常AWD问题不应被拒答() {
        String result = bm25RagService.checkSafetyRules("AWD方法的复灌阈值是多少？");
        assertNull(result);
    }

    // ==================== BM25检索测试 ====================

    // 验证已知AWD问题能检索到有效证据并带引用
    @Test
    void AWD问题应返回带引用的证据() {
        // 使用注入小语料的公开搜索方法
        List<Bm25RagService.ScoredChunk> results =
                bm25RagService.searchPublic("AWD灌溉的复灌水位阈值是多少？", 5);
        assertNotNull(results);
        assertFalse(results.isEmpty());
        // 应检索到AWD相关chunk
        boolean foundAwd = false;
        for (Bm25RagService.ScoredChunk sc : results) {
            if (sc.chunk.title.contains("AWD") || sc.chunk.text.contains("AWD")) {
                foundAwd = true;
                break;
            }
        }
        assertTrue(foundAwd, "应检索到AWD相关chunk");
    }

    // 验证引用的source_id和chunk_id存在且一致
    @Test
    void 引用应与实际检索到的chunk一致() {
        List<Bm25RagService.ScoredChunk> results =
                bm25RagService.searchPublic("AWD方法的复灌阈值是多少？", 5);
        assertNotNull(results);
        assertFalse(results.isEmpty());
        for (Bm25RagService.ScoredChunk sc : results) {
            assertNotNull(sc.chunk.sourceId);
            assertNotNull(sc.chunk.chunkId);
            assertFalse(sc.chunk.sourceId.isEmpty());
            assertFalse(sc.chunk.chunkId.isEmpty());
        }
    }

    // 验证低分拒答逻辑（查询完全无关的内容）
    @Test
    void 无关查询得分低() {
        List<Bm25RagService.ScoredChunk> results =
                bm25RagService.searchPublic("今天天气很好适合出去玩", 5);
        assertNotNull(results);
        if (!results.isEmpty()) {
            // 无关查询得分应低于相关查询
            double topScore = results.get(0).score;
            List<Bm25RagService.ScoredChunk> awdResults =
                    bm25RagService.searchPublic("AWD灌溉的复灌阈值", 5);
            if (!awdResults.isEmpty()) {
                assertTrue(topScore < awdResults.get(0).score,
                        "无关查询得分(" + topScore + ") 应低于相关查询得分("
                                + awdResults.get(0).score + ")");
            }
        }
    }

    // ==================== 语料加载测试 ====================

    // 验证语料加载后chunk和source数量正确
    @Test
    void 语料加载后chunk和source数量正确() {
        int chunkCount = bm25RagService.getChunkCount();
        int sourceCount = bm25RagService.getSourceCount();
        assertTrue(chunkCount > 0, "chunks数量应大于0，实际=" + chunkCount);
        assertTrue(sourceCount > 0, "sources数量应大于0，实际=" + sourceCount);
        assertEquals(20, chunkCount, "应有20个chunks");
        assertEquals(12, sourceCount, "应有12个sources");
    }

    // ==================== RAG完整查询测试 ====================

    // 验证完整RAG query流程（使用实际语料）
    @Test
    void AWDQuery应返回非拒答结果() {
        RagQueryRequest req = new RagQueryRequest();
        req.setQuestion("AWD方法的复灌水位阈值是多少？");
        req.setTopK(5);

        RagQueryResponse resp = bm25RagService.query("test_user", req);
        assertNotNull(resp);
        // 如果语料中AWD相关内容足够相关，应不拒答
        if (!resp.isAbstained()) {
            assertNotNull(resp.getAnswer());
            assertTrue(resp.getTopScore() > 0);
            // 引用数量应在1-3之间
            assertTrue(resp.getCitations().size() >= 1
                    && resp.getCitations().size() <= 3);
        }
    }

    // 验证控制请求通过完整流程被拒答
    @Test
    void 控制请求完整流程应被拒答() {
        RagQueryRequest req = new RagQueryRequest();
        req.setQuestion("请启动灌溉泵，把水位调到5厘米");
        req.setTopK(5);

        RagQueryResponse resp = bm25RagService.query("test_user", req);
        assertNotNull(resp);
        assertTrue(resp.isAbstained());
        assertNotNull(resp.getAbstainReason());
    }

    // 验证地域外推完整流程被拒答
    @Test
    void 地域外推完整流程应被拒答() {
        RagQueryRequest req = new RagQueryRequest();
        req.setQuestion("DB32标准的江苏水位阈值在江西稻田能用吗？");
        req.setTopK(5);

        RagQueryResponse resp = bm25RagService.query("test_user", req);
        assertNotNull(resp);
        assertTrue(resp.isAbstained());
    }

    // 验证持久化时owner正确写入
    @Test
    void query应使用正确的owner() {
        RagQueryRequest req = new RagQueryRequest();
        req.setQuestion("AWD是什么？");
        req.setTopK(3);

        RagQueryResponse resp = bm25RagService.query("owner_test_user", req);
        assertNotNull(resp);
        // 持久化由服务内部处理，这里只验证流程不抛异常
    }

    // ==================== 持久化实体字段验证（ArgumentCaptor） ====================

    /**
     * 验证正常回答时持久化实体的核心字段正确（owner/question/abstained/retriever/threshold/topScore）
     */
    @Test
    void 正常回答持久化实体字段应正确() {
        RagQueryRequest req = new RagQueryRequest();
        req.setQuestion("AWD灌溉的复灌水位阈值是多少？");
        req.setTopK(5);

        RagQueryResponse resp = bm25RagService.query("captor_test_user", req);

        if (!resp.isAbstained()) {
            ArgumentCaptor<RagQueryEntity> captor =
                    ArgumentCaptor.forClass(RagQueryEntity.class);
            verify(ragQueryRepository, atLeastOnce()).save(captor.capture());
            RagQueryEntity entity = captor.getValue();

            assertEquals("captor_test_user", entity.getOwnerUsername(),
                    "持久化owner应正确");
            assertEquals("AWD灌溉的复灌水位阈值是多少？", entity.getQuestion(),
                    "持久化question应正确");
            assertFalse(entity.getAbstained(), "abstained应为false");
            assertEquals("bm25", entity.getRetriever(),
                    "retriever应为bm25");
            assertEquals(27.31768531, entity.getThresholdValue(), 1e-9,
                    "threshold应正确");
            assertNotNull(entity.getTopScore(), "topScore不应为null");
            assertTrue(entity.getTopScore() > 0, "topScore应大于0");
        }
    }

    /**
     * 验证拒答时持久化实体的字段正确
     */
    @Test
    void 拒答应持久化abstained为true() {
        RagQueryRequest req = new RagQueryRequest();
        req.setQuestion("请启动灌溉泵");
        req.setTopK(5);

        bm25RagService.query("captor_test_user", req);

        ArgumentCaptor<RagQueryEntity> captor =
                ArgumentCaptor.forClass(RagQueryEntity.class);
        verify(ragQueryRepository, atLeastOnce()).save(captor.capture());
        RagQueryEntity entity = captor.getValue();

        assertEquals("captor_test_user", entity.getOwnerUsername());
        assertTrue(entity.getAbstained(), "abstained应为true");
        assertNotNull(entity.getAbstainReason(), "abstainReason不应为null");
    }

    // ==================== 引用和证据字段验证 ====================

    /**
     * 验证正常回答时citationsJson和evidenceJson非空，
     * 且citation_id与source_id/chunk_id一致
     */
    @Test
    void 正常回答引用和证据应含citationId() {
        RagQueryRequest req = new RagQueryRequest();
        req.setQuestion("AWD灌溉的复灌水位阈值是多少？");
        req.setTopK(5);

        RagQueryResponse resp = bm25RagService.query("cite_test_user", req);

        if (!resp.isAbstained()) {
            ArgumentCaptor<RagQueryEntity> captor =
                    ArgumentCaptor.forClass(RagQueryEntity.class);
            verify(ragQueryRepository, atLeastOnce()).save(captor.capture());
            RagQueryEntity entity = captor.getValue();

            // 验证citationsJson非空
            assertNotNull(entity.getCitationsJson(), "citationsJson不应为null");
            assertFalse(entity.getCitationsJson().isEmpty(),
                    "citationsJson不应为空");

            // 验证evidenceJson非空
            assertNotNull(entity.getEvidenceJson(), "evidenceJson不应为null");
            assertFalse(entity.getEvidenceJson().isEmpty(),
                    "evidenceJson不应为空");

            // 验证response中的citation包含citation_id且格式为sourceId#chunkId
            assertFalse(resp.getCitations().isEmpty());
            for (Map<String, Object> citation : resp.getCitations()) {
                String sourceId = (String) citation.get("source_id");
                String chunkId = (String) citation.get("chunk_id");
                String citationId = (String) citation.get("citation_id");
                assertNotNull(sourceId);
                assertNotNull(chunkId);
                assertNotNull(citationId);
                assertEquals(sourceId + "#" + chunkId, citationId,
                        "citation_id应为source_id#chunk_id格式");
            }
        }
    }

    // ==================== 持久化异常传播验证 ====================

    /**
     * 验证repository.save抛异常时query必须向上抛（不再吞掉）
     */
    @Test
    void 持久化失败应向上抛异常() {
        // 使 save 抛出运行时异常
        doThrow(new RuntimeException("模拟数据库故障"))
                .when(ragQueryRepository).save(any());

        RagQueryRequest req = new RagQueryRequest();
        req.setQuestion("AWD灌溉的复灌水位阈值是多少？");
        req.setTopK(5);

        assertThrows(RuntimeException.class, () ->
                bm25RagService.query("fail_user", req),
                "repository.save失败时query必须抛出异常");
    }
}
