package com.ruoyi.iotsystem.dto;

import java.util.List;

/**
 * 分页统一响应体，配合 Spring Data Pageable 使用
 * <pre>{@code
 * {
 *   "content": [...],     // 当前页数据
 *   "page": 0,            // 当前页码 (0-based)
 *   "size": 20,           // 每页条数
 *   "totalElements": 150, // 总记录数
 *   "totalPages": 8       // 总页数
 * }
 * }</pre>
 */
public class PagedResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public PagedResponse() {}

    public PagedResponse(List<T> content, int page, int size, long totalElements, int totalPages) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    /** 从 Spring Data Page 对象构建 */
    public static <T> PagedResponse<T> of(org.springframework.data.domain.Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    // ==================== Getters & Setters ====================

    public List<T> getContent() { return content; }
    public void setContent(List<T> content) { this.content = content; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
}
