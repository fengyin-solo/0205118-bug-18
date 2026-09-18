package com.redtourism.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("spot_suggestion")
public class SpotSuggestion implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long spotId;
    private String spotName;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String reason;
    private String status;
    private String rejectReason;
    private Date createTime;

    @TableField(exist = false)
    private String username;

    /** 字段中文名，列表展示用，避免直接显示字段 key 造成与内容错位 */
    @TableField(exist = false)
    private String fieldLabel;

    /** 应用审核时景点该字段的当前值，审核人可据此判断是否已被更早的建议更新 */
    @TableField(exist = false)
    private String currentValue;

    /** 该建议是否为同景点同字段下最早的一条待处理建议（决定能否按顺序通过） */
    @TableField(exist = false)
    private Boolean earliestPending;
}
