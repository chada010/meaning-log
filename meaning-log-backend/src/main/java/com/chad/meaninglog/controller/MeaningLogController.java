package com.chad.meaninglog.controller;

import com.chad.meaninglog.dto.LogNavigationResponse;
import com.chad.meaninglog.dto.MeaningLogRequest;
import com.chad.meaninglog.dto.MeaningLogResponse;
import com.chad.meaninglog.entity.LogImage;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.service.MeaningLogService;
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

@CrossOrigin(origins = LOCAL_FRONTEND_ORIGIN)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/logs")
public class MeaningLogController {

    private final MeaningLogService meaningLogService;

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

    @GetMapping("/{id}")
    public MeaningLogResponse findById(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) {
        return meaningLogService.findById(user, id);
    }

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

    @GetMapping("/{id}/navigation")
    public LogNavigationResponse findNavigation(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) {
        return meaningLogService.findNavigation(user, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeaningLogResponse create(
            @AuthenticationPrincipal UserAccount user,
            @Valid @RequestBody MeaningLogRequest request
    ) {
        return meaningLogService.create(user, request);
    }

    @PutMapping("/{id}")
    public MeaningLogResponse update(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id,
            @Valid @RequestBody MeaningLogRequest request
    ) {
        return meaningLogService.update(user, id, request);
    }

    @PutMapping("/{id}/favorite")
    public MeaningLogResponse updateFavorite(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id,
            @RequestParam boolean favorite
    ) {
        return meaningLogService.updateFavorite(user, id, favorite);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserAccount user,
            @PathVariable Long id
    ) {
        meaningLogService.delete(user, id);
    }

}
