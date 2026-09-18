package com.redtourism.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.redtourism.common.Constants;
import com.redtourism.common.Result;
import com.redtourism.entity.SpotSuggestion;
import com.redtourism.entity.User;
import com.redtourism.mapper.SpotSuggestionMapper;
import com.redtourism.service.SpotSuggestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.List;

@RestController
@RequestMapping("/api/spotSuggestion")
public class SpotSuggestionController {

    @Autowired private SpotSuggestionMapper mapper;
    @Autowired private SpotSuggestionService suggestionService;

    @GetMapping("/submit")
    public Result<String> submit(@RequestParam Long spotId,
                                  @RequestParam String fieldName,
                                  @RequestParam(required = false) String newValue,
                                  @RequestParam(required = false) String reason,
                                  HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user == null) return Result.error(401, "请先登录");
        // 字段合法性与格式在服务层统一校验，非法时返回具体原因；空内容允许提交（应用时保留原值）
        suggestionService.submit(user.getId(), spotId, fieldName,
                newValue != null ? newValue : "", reason);
        return Result.success("更正建议已提交，等待管理员审核", null);
    }

    @GetMapping("/my")
    public Result<List<SpotSuggestion>> myList(HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user == null) return Result.error(401, "请先登录");
        return Result.success(mapper.selectList(
                new LambdaQueryWrapper<SpotSuggestion>()
                        .eq(SpotSuggestion::getUserId, user.getId())
                        .orderByDesc(SpotSuggestion::getCreateTime)));
    }
}
