package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.LogNavigationResponse;
import com.chad.meaninglog.dto.MeaningLogRequest;
import com.chad.meaninglog.dto.MeaningLogResponse;
import com.chad.meaninglog.entity.LogImage;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.service.MeaningLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import static com.chad.meaninglog.web.WebConstants.LOCAL_FRONTEND_ORIGIN;

@Tag(name = "日志", description = "日志的增删改查与图片、导航、收藏等相关操作")
@CrossOrigin(origins = LOCAL_FRONTEND_ORIGIN)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/logs")
public class MeaningLogController {

    private final MeaningLogService meaningLogService;

    @Operation(summary = "查询日志列表", description = "支持按日期、关键词、标签、收藏筛选")
    @GetMapping
    public List<MeaningLogResponse> findAll(
            @AuthenticationPrincipal UserAccount user,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestParam(required = false)
            String keyword,
            @RequestParam(required = false)
            String tag,
            @RequestParam(required = false)
            Boolean favorite
    ) {
        return meaningLogService.findAll(user, date, keyword, tag, favorite);
    }

    @Operation(summary = "获取日志详情")
    @GetMapping("/{id}")
    public MeaningLogResponse findById(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) {
        return meaningLogService.findById(user, id);
    }

    @Operation(summary = "获取日志图片二进制", description = "返回原始 content-type，可直接嵌入 <img>")
    @GetMapping("/images/{imageId}")
    public ResponseEntity<byte[]> findImage(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long imageId
    ) {
        LogImage image = meaningLogService.getLogImage(user, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .body(image.getData());
    }

    @Operation(summary = "获取上一篇/下一篇日志导航信息")
    @GetMapping("/{id}/navigation")
    public LogNavigationResponse findNavigation(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) {
        return meaningLogService.findNavigation(user, id);
    }

    @Operation(summary = "创建日志")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeaningLogResponse create(
            @AuthenticationPrincipal UserAccount user,
            @Valid @RequestBody MeaningLogRequest request
    ) {
        return meaningLogService.create(user, request);
    }

    @Operation(summary = "更新日志")
    @PutMapping("/{id}")
    public MeaningLogResponse update(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id,
            @Valid @RequestBody MeaningLogRequest request
    ) {
        return meaningLogService.update(user, id, request);
    }

    @Operation(summary = "更新日志收藏状态")
    @PutMapping("/{id}/favorite")
    public MeaningLogResponse updateFavorite(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id,
            @RequestParam boolean favorite
    ) {
        return meaningLogService.updateFavorite(user, id, favorite);
    }

    @Operation(summary = "删除日志")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) {
        meaningLogService.delete(user, id);
    }

}
