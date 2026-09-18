package com.redtourism.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.redtourism.entity.SpotSuggestion;

public interface SpotSuggestionService extends IService<SpotSuggestion> {

    /** 用户提交更正建议（提交时做基础格式校验） */
    SpotSuggestion submit(Long userId, Long spotId, String fieldName, String newValue, String reason);

    /**
     * 审核通过并应用更正。
     * 应用失败（字段非法/景点不存在/存在更早的同字段待处理建议）时抛出 IllegalArgumentException，
     * 建议保持 PENDING，可修改数据后重试或驳回。
     */
    String approve(Long id);

    /** 审核列表：待处理按提交时间升序（保证重复建议先提交先处理），其余状态按时间倒序 */
    IPage<SpotSuggestion> listForReview(int page, int size, String status);
}
