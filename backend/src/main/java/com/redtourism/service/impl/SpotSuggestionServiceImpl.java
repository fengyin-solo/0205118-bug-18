package com.redtourism.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redtourism.common.SpotSuggestionFields;
import com.redtourism.entity.ScenicSpot;
import com.redtourism.entity.SpotSuggestion;
import com.redtourism.entity.User;
import com.redtourism.mapper.SpotSuggestionMapper;
import com.redtourism.mapper.UserMapper;
import com.redtourism.service.MessageService;
import com.redtourism.service.ScenicSpotService;
import com.redtourism.service.SpotSuggestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class SpotSuggestionServiceImpl
        extends ServiceImpl<SpotSuggestionMapper, SpotSuggestion>
        implements SpotSuggestionService {

    @Autowired
    private ScenicSpotService spotService;
    @Autowired
    private MessageService messageService;
    @Autowired
    private UserMapper userMapper;

    @Override
    public SpotSuggestion submit(Long userId, Long spotId, String fieldName, String newValue, String reason) {
        ScenicSpot spot = spotService.getById(spotId);
        if (spot == null) {
            throw new IllegalArgumentException("景点不存在，无法提交更正");
        }
        if (!SpotSuggestionFields.isSupported(fieldName)) {
            throw new IllegalArgumentException("不支持的更正字段：" + fieldName);
        }
        // 提交时提前拦截明显非法的格式，审核通过时仍会再次校验（权威校验）
        String invalid = SpotSuggestionFields.validate(fieldName, newValue);
        if (invalid != null) {
            throw new IllegalArgumentException(invalid);
        }
        SpotSuggestion s = new SpotSuggestion();
        s.setUserId(userId);
        s.setSpotId(spotId);
        s.setSpotName(spot.getName());
        s.setFieldName(fieldName);
        s.setNewValue(newValue != null ? newValue.trim() : "");
        s.setReason(reason);
        s.setStatus("PENDING");
        s.setCreateTime(new Date());
        // 快照提交时的原值，便于审核对比
        s.setOldValue(SpotSuggestionFields.readValue(spot, fieldName));
        save(s);
        return s;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String approve(Long id) {
        SpotSuggestion s = getById(id);
        if (s == null) {
            throw new IllegalArgumentException("更正建议不存在");
        }
        if (!"PENDING".equals(s.getStatus())) {
            throw new IllegalArgumentException("该建议已处理，请勿重复操作");
        }

        ScenicSpot spot = spotService.getById(s.getSpotId());
        if (spot == null) {
            throw new IllegalArgumentException("关联景点已不存在，无法应用更正，建议保留待处理可稍后重试或驳回");
        }

        // 1. 字段格式校验：不合法时不允许通过，并指明具体哪一项无效，建议保持待处理可重试
        String invalid = SpotSuggestionFields.validate(s.getFieldName(), s.getNewValue());
        if (invalid != null) {
            throw new IllegalArgumentException("【" + SpotSuggestionFields.label(s.getFieldName()) + "】" + invalid
                    + "，暂无法通过，请修正景点数据后重试或驳回该建议");
        }

        // 2. 重复提交按时间顺序处理：同景点同字段存在更早的待处理建议时，必须先处理早的一条
        SpotSuggestion earlier = baseMapper.selectOne(new LambdaQueryWrapper<SpotSuggestion>()
                .eq(SpotSuggestion::getSpotId, s.getSpotId())
                .eq(SpotSuggestion::getFieldName, s.getFieldName())
                .eq(SpotSuggestion::getStatus, "PENDING")
                .and(w -> w.lt(SpotSuggestion::getCreateTime, s.getCreateTime())
                        .or().eq(SpotSuggestion::getCreateTime, s.getCreateTime())
                        .lt(SpotSuggestion::getId, s.getId()))
                .orderByAsc(SpotSuggestion::getCreateTime)
                .orderByAsc(SpotSuggestion::getId)
                .last("LIMIT 1"));
        if (earlier != null) {
            throw new IllegalArgumentException("该字段还有一条更早提交的待处理建议（"
                    + (earlier.getId()) + "），请先按时间顺序处理更早的建议");
        }

        // 3. 记录应用前该字段的实际值（用于已通过列表展示，避免字段错位），
        //    再把新值应用到景点；空值不写入，保留原值。任一步失败整体回滚，建议保留待处理
        String valueBeforeApply = SpotSuggestionFields.readValue(spot, s.getFieldName());
        boolean changed = SpotSuggestionFields.applyValue(spot, s.getFieldName(), s.getNewValue());
        spotService.updateById(spot);

        // 4. 应用成功后再标记通过，并把该建议的旧值快照更新为应用前的实际值，
        //    使处理列表里已通过记录展示的“旧值 → 新值”与实际生效内容一致、不错位
        s.setStatus("APPROVED");
        s.setRejectReason(null);
        s.setOldValue(valueBeforeApply);
        updateById(s);

        // 5. 同步刷新同景点同字段其余待处理建议的旧值快照，避免后处理的建议与列表内容错位
        List<SpotSuggestion> pendingSame = list(new LambdaQueryWrapper<SpotSuggestion>()
                .eq(SpotSuggestion::getSpotId, s.getSpotId())
                .eq(SpotSuggestion::getFieldName, s.getFieldName())
                .eq(SpotSuggestion::getStatus, "PENDING"));
        String freshValue = SpotSuggestionFields.readValue(spot, s.getFieldName());
        for (SpotSuggestion p : pendingSame) {
            p.setOldValue(freshValue);
            updateById(p);
        }

        messageService.sendMessage(s.getUserId(), "您的景点更正建议已通过",
                "您提交的关于【" + s.getSpotName() + "】“"
                        + SpotSuggestionFields.label(s.getFieldName()) + "”的更正已被采纳，感谢您的贡献！");

        return changed ? "已通过并应用" : "新内容为空，已保留原字段值";
    }

    @Override
    public IPage<SpotSuggestion> listForReview(int page, int size, String status) {
        LambdaQueryWrapper<SpotSuggestion> w = new LambdaQueryWrapper<>();
        if (status == null || status.isEmpty()) {
            // 全部：待处理（按时间升序，先提交先处理）排在最前，已处理记录按时间倒序在后
            w.last("ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END, create_time ASC, id ASC");
        } else if ("PENDING".equals(status)) {
            // 待处理：提交时间升序，先提交先审核
            w.last("ORDER BY create_time ASC, id ASC");
        } else {
            w.last("ORDER BY create_time DESC, id DESC");
        }
        IPage<SpotSuggestion> result = page(new Page<>(page, size), w);
        for (SpotSuggestion r : result.getRecords()) {
            User u = userMapper.selectById(r.getUserId());
            r.setUsername(u != null && u.getNickname() != null ? u.getNickname()
                    : (u != null ? u.getUsername() : "--"));
            r.setFieldLabel(SpotSuggestionFields.label(r.getFieldName()));
            ScenicSpot spot = spotService.getById(r.getSpotId());
            r.setCurrentValue(spot != null ? SpotSuggestionFields.readValue(spot, r.getFieldName()) : "");
            if ("PENDING".equals(r.getStatus())) {
                // 是否为同景点同字段最早的一条待处理建议
                SpotSuggestion earliest = baseMapper.selectOne(new LambdaQueryWrapper<SpotSuggestion>()
                        .eq(SpotSuggestion::getSpotId, r.getSpotId())
                        .eq(SpotSuggestion::getFieldName, r.getFieldName())
                        .eq(SpotSuggestion::getStatus, "PENDING")
                        .orderByAsc(SpotSuggestion::getCreateTime)
                        .orderByAsc(SpotSuggestion::getId)
                        .last("LIMIT 1"));
                r.setEarliestPending(earliest != null && earliest.getId().equals(r.getId()));
            } else {
                r.setEarliestPending(false);
            }
        }
        return result;
    }
}
