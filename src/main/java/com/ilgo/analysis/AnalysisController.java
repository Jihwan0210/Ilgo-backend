package com.ilgo.analysis;

import com.ilgo.analysis.dto.ActionToggleRequest;
import com.ilgo.analysis.dto.ActionToggleResponse;
import com.ilgo.analysis.dto.AnalysisListResponse;
import com.ilgo.analysis.dto.AnalysisResponse;
import com.ilgo.analysis.dto.AnalyzeRequest;
import com.ilgo.analysis.dto.FeedbackRequest;
import com.ilgo.analysis.dto.FeedbackResponse;
import com.ilgo.analysis.dto.RepliesRegenerateRequest;
import com.ilgo.analysis.dto.RepliesRegenerateResponse;
import com.ilgo.common.ApiException;
import com.ilgo.common.CurrentUser;
import com.ilgo.common.ErrorCode;
import com.ilgo.user.OutputMode;
import com.ilgo.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

@RestController
@RequestMapping("/v1/analyses")
@Tag(name = "Analyses", description = "이미지 분석")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Operation(summary = "이미지 분석 (JSON)",
            description = "이미지 base64 를 받아 분석. 저장 정책: status=DONE 이고 문서 분류가 저장 대상(notice/announcement/message/contract)이며 save=true 일 때만 저장(201). 판독 실패·문서 아님/미지·save=false 는 저장하지 않고 200(id=null).")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalysisResponse> analyzeJson(@Valid @RequestBody AnalyzeRequest request,
                                                        @CurrentUser User user) {
        return respond(analysisService.analyze(user, request));
    }

    @Operation(summary = "이미지 분석 (multipart)", description = "이미지 파일을 업로드해 분석한다. 저장 정책은 JSON 엔드포인트와 동일(저장 201 / 스킵·실패 200 id=null).")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalysisResponse> analyzeMultipart(@RequestParam("image") MultipartFile image,
                                             @RequestParam(value = "output_mode", required = false) String outputMode,
                                             @RequestParam(value = "lang", required = false) String lang,
                                             @RequestParam(value = "save", required = false) Boolean save,
                                             @RequestParam(value = "hint", required = false) String hint,
                                             @CurrentUser User user) {
        if (image == null || image.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "image 파일이 필요합니다.");
        }
        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "image 파일을 읽지 못했습니다.");
        }
        String base64 = Base64.getEncoder().encodeToString(bytes);
        AnalyzeRequest request = new AnalyzeRequest(
                base64,
                image.getContentType(),
                OutputMode.fromJson(outputMode),
                lang,
                save,
                hint);
        return respond(analysisService.analyze(user, request));
    }

    /** 저장되면 201(id 존재), 저장 스킵/실패면 200(id=null). */
    private ResponseEntity<AnalysisResponse> respond(AnalysisResponse result) {
        HttpStatus code = result.id() != null ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(code).body(result);
    }

    @Operation(summary = "분석 단건 조회", description = "소유자만 조회 가능.")
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalysisResponse getOne(@PathVariable String id, @CurrentUser User user) {
        return analysisService.getOne(user, id);
    }

    @Operation(summary = "보관함 목록", description = "커서 기반 페이지네이션. 요약 필드만 반환.")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalysisListResponse list(@RequestParam(required = false) Integer limit,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(required = false) Boolean saved,
                                     @CurrentUser User user) {
        return analysisService.list(user, limit, cursor, saved);
    }

    @Operation(summary = "분석 삭제", description = "하위 risks/actions/reply_drafts 도 함께 삭제.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @CurrentUser User user) {
        analysisService.delete(user, id);
    }

    @Operation(summary = "할 일 완료 토글")
    @PatchMapping(value = "/{id}/actions/{actionId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ActionToggleResponse toggleAction(@PathVariable String id,
                                             @PathVariable String actionId,
                                             @Valid @RequestBody ActionToggleRequest request,
                                             @CurrentUser User user) {
        return analysisService.toggleAction(user, id, actionId, request.isDone());
    }

    @Operation(summary = "답장 재생성 (native 전용)", description = "easy_korean 분석에 요청하면 409.")
    @PostMapping(value = "/{id}/replies:regenerate", produces = MediaType.APPLICATION_JSON_VALUE)
    public RepliesRegenerateResponse regenerateReplies(@PathVariable String id,
                                                       @RequestBody(required = false) RepliesRegenerateRequest request,
                                                       @CurrentUser User user) {
        String tone = request != null ? request.tone() : null;
        return analysisService.regenerateReplies(user, id, tone);
    }

    @Operation(summary = "정확도 피드백")
    @PostMapping(value = "/{id}/feedback", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FeedbackResponse> feedback(@PathVariable String id,
                                                     @Valid @RequestBody FeedbackRequest request,
                                                     @CurrentUser User user) {
        FeedbackResponse body = analysisService.addFeedback(user, id, request.isHelpful(), request.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
