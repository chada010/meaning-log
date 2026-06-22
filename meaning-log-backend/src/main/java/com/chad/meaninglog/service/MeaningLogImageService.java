package com.chad.meaninglog.service;

import com.chad.meaninglog.dto.LogImageRequest;
import com.chad.meaninglog.entity.LogImage;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;
import com.chad.meaninglog.repository.LogImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MeaningLogImageService {

    private final LogImageRepository logImageRepository;

    @Transactional(readOnly = true)
    public List<LogImage> loadImages(MeaningLog meaningLog) {
        return logImageRepository.findByMeaningLogOrderByDisplayOrderAscIdAsc(meaningLog);
    }

    @Transactional(readOnly = true)
    public LogImage getLogImage(UserAccount user, Long imageId) {
        return logImageRepository.findByIdAndMeaningLogUser(imageId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
    }

    @Transactional
    public void replaceImages(MeaningLog meaningLog, List<LogImageRequest> images) {
        logImageRepository.deleteByMeaningLog(meaningLog);
        if (images == null || images.isEmpty()) {
            return;
        }

        for (int index = 0; index < images.size(); index++) {
            LogImageRequest imageRequest = images.get(index);
            ParsedImage parsedImage = parseImage(imageRequest);
            LogImage image = new LogImage();
            image.setMeaningLog(meaningLog);
            image.setFileName(blankToFallback(imageRequest.getFileName(), "log-image-" + (index + 1)));
            image.setCaption(imageRequest.getCaption() == null ? "" : imageRequest.getCaption().trim());
            image.setContentType(parsedImage.contentType());
            image.setFileSize(parsedImage.data().length);
            image.setDisplayOrder(index);
            image.setData(parsedImage.data());
            logImageRepository.save(image);
        }
    }

    @Transactional
    public void deleteImages(MeaningLog meaningLog) {
        logImageRepository.deleteByMeaningLog(meaningLog);
    }

    private ParsedImage parseImage(LogImageRequest request) {
        if (request == null || request.getDataUrl() == null || request.getDataUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片内容不能为空");
        }

        String dataUrl = request.getDataUrl().trim();
        int commaIndex = dataUrl.indexOf(',');
        String metadata = commaIndex > 0 ? dataUrl.substring(0, commaIndex) : "";
        String base64 = commaIndex > 0 ? dataUrl.substring(commaIndex + 1) : dataUrl;
        String contentType = request.getContentType();

        if (metadata.startsWith("data:") && metadata.contains(";base64")) {
            contentType = metadata.substring("data:".length(), metadata.indexOf(";base64"));
        }

        if (contentType == null || !contentType.matches("image/(png|jpeg|jpg|webp|gif)")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 PNG、JPG、WEBP、GIF 图片");
        }

        byte[] data;
        try {
            data = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片内容不是有效的 Base64", ex);
        }

        if (data.length > 2 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单张图片不能超过 2MB");
        }

        return new ParsedImage(contentType.equals("image/jpg") ? "image/jpeg" : contentType, data);
    }

    private String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record ParsedImage(String contentType, byte[] data) {
    }
}
