package com.ilgo.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilgo.ai.AiGatewayClient;
import com.ilgo.ai.AiAnalysis;
import com.ilgo.ai.AiParseException;
import com.ilgo.ai.AiResponseParser;
import com.ilgo.analysis.dto.ActionDto;
import com.ilgo.analysis.dto.ActionToggleResponse;
import com.ilgo.analysis.dto.AnalysisListItem;
import com.ilgo.analysis.dto.AnalysisListResponse;
import com.ilgo.analysis.dto.AnalysisResponse;
import com.ilgo.analysis.dto.AnalyzeRequest;
import com.ilgo.analysis.dto.CardDto;
import com.ilgo.analysis.dto.FeedbackResponse;
import com.ilgo.analysis.dto.RepliesRegenerateResponse;
import com.ilgo.analysis.dto.ReplyDraftDto;
import com.ilgo.analysis.dto.RiskDto;
import com.ilgo.ai.AiPrompt;
import com.ilgo.common.ApiException;
import com.ilgo.common.ErrorCode;
import com.ilgo.user.OutputMode;
import com.ilgo.user.User;
import com.ilgo.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of("image/jpeg", "image/png");
    private static final int MAX_AI_ATTEMPTS = 2;
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 20;

    private final AnalysisRepository analysisRepository;
    private final ActionRepository actionRepository;
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final AiGatewayClient aiClient;
    private final AiResponseParser parser;
    private final ObjectMapper objectMapper;

    private final boolean mockMode;
    private final boolean mockSave;
    private final long maxImageBytes;

    public AnalysisService(AnalysisRepository analysisRepository,
                           ActionRepository actionRepository,
                           FeedbackRepository feedbackRepository,
                           UserRepository userRepository,
                           AiGatewayClient aiClient,
                           AiResponseParser parser,
                           ObjectMapper objectMapper,
                           @Value("${app.mock-mode:false}") boolean mockMode,
                           @Value("${app.mock-save:false}") boolean mockSave,
                           @Value("${app.max-image-bytes:5242880}") long maxImageBytes) {
        this.analysisRepository = analysisRepository;
        this.actionRepository = actionRepository;
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
        this.aiClient = aiClient;
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.mockMode = mockMode;
        this.mockSave = mockSave;
        this.maxImageBytes = maxImageBytes;
    }

    public AnalysisResponse analyze(User user, AnalyzeRequest request) {
        OutputMode outputMode = request.outputMode() != null ? request.outputMode() : user.getDefaultOutputMode();
        String lang = (request.lang() != null && !request.lang().isBlank()) ? request.lang() : user.getDefaultLang();
        boolean save = request.save() == null || request.save();

        validateImage(request.mediaType(), request.imageBase64());

        if (mockMode) {
            return handleMock(user, outputMode, request.hint(), save);
        }
        return handleReal(user, outputMode, lang, request.imageBase64(), request.mediaType(), save);
    }

    // ---------------------------------------------------------------- read / list

    @Transactional(readOnly = true)
    public AnalysisResponse getOne(User user, String idStr) {
        return toResponse(getOwned(user, idStr));
    }

    @Transactional(readOnly = true)
    public AnalysisListResponse list(User user, Integer limit, String cursor, Boolean saved) {
        if (mockMode) {
            return readMockList();
        }
        int size = normalizeLimit(limit);
        boolean savedOnly = saved == null || saved; // 기본: 보관함(저장된 것만)
        PageRequest page = PageRequest.of(0, size + 1); // 다음 페이지 존재 여부 판단용 +1

        List<Analysis> rows;
        if (cursor == null || cursor.isBlank()) {
            rows = analysisRepository.findFirstPage(user.getId(), savedOnly, page);
        } else {
            CursorCodec.Cursor c = CursorCodec.decode(cursor);
            rows = analysisRepository.findAfterCursor(user.getId(), savedOnly, c.createdAt(), c.id(), page);
        }

        String nextCursor = null;
        if (rows.size() > size) {
            Analysis last = rows.get(size - 1);
            nextCursor = CursorCodec.encode(last.getCreatedAt(), last.getId());
            rows = rows.subList(0, size);
        }

        List<AnalysisListItem> items = new ArrayList<>(rows.size());
        for (Analysis a : rows) {
            items.add(new AnalysisListItem(
                    str(a.getId()), a.getDocType(), a.getSummaryOneLine(),
                    a.topRiskLevel(), a.getCardDeadline(), toUtc(a.getCreatedAt())));
        }
        return new AnalysisListResponse(items, nextCursor);
    }

    @Transactional
    public void delete(User user, String idStr) {
        Analysis a = getOwned(user, idStr);
        analysisRepository.delete(a); // 자식은 cascade/orphanRemoval 로 삭제
    }

    // ---------------------------------------------------------------- P2

    @Transactional
    public ActionToggleResponse toggleAction(User user, String idStr, String actionIdStr, boolean isDone) {
        Analysis analysis = getOwned(user, idStr);
        UUID actionId = parseUuid(actionIdStr);
        Action action = actionRepository.findByIdAndAnalysisId(actionId, analysis.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        action.setDone(isDone);
        actionRepository.save(action);
        return new ActionToggleResponse(str(action.getId()), action.isDone());
    }

    @Transactional
    public RepliesRegenerateResponse regenerateReplies(User user, String idStr, String tone) {
        Analysis analysis = getOwned(user, idStr);
        if (analysis.getOutputMode() != OutputMode.NATIVE) {
            throw new ApiException(ErrorCode.NOT_NATIVE_MODE);
        }

        List<ReplyDraft> newDrafts;
        if (mockMode) {
            AnalysisResponse wage = readMock("mock/analyze_native_wage.json");
            newDrafts = new ArrayList<>();
            if (wage.replyDrafts() != null) {
                for (ReplyDraftDto d : wage.replyDrafts()) {
                    newDrafts.add(new ReplyDraft(d.korean(), d.noteInLang()));
                }
            }
        } else {
            newDrafts = regenerateViaAi(analysis, tone);
        }

        analysis.clearReplyDrafts();
        for (ReplyDraft d : newDrafts) {
            analysis.addReplyDraft(d);
        }
        Analysis saved = analysisRepository.save(analysis);

        List<ReplyDraftDto> dtos = new ArrayList<>();
        for (ReplyDraft d : saved.getReplyDrafts()) {
            dtos.add(new ReplyDraftDto(str(d.getId()), d.getKorean(), d.getNoteInLang()));
        }
        return new RepliesRegenerateResponse(dtos);
    }

    private List<ReplyDraft> regenerateViaAi(Analysis analysis, String tone) {
        for (int attempt = 1; attempt <= MAX_AI_ATTEMPTS; attempt++) {
            try {
                String text = aiClient.completeText(
                        AiPrompt.REPLY_SYSTEM,
                        AiPrompt.replyUserText(analysis.getLang(), analysis.getOriginalText(),
                                analysis.getExplainedText(), tone));
                AiAnalysis parsed = parser.parse(text);
                List<ReplyDraft> drafts = new ArrayList<>();
                if (parsed.replyDrafts() != null) {
                    for (AiAnalysis.ReplyDraft d : parsed.replyDrafts()) {
                        drafts.add(new ReplyDraft(d.korean(), d.noteInLang()));
                    }
                }
                return drafts;
            } catch (AiParseException e) {
                log.warn("답장 재생성 파싱 실패 (attempt {}/{})", attempt, MAX_AI_ATTEMPTS);
            }
        }
        throw new ApiException(ErrorCode.AI_PARSE_FAILED);
    }

    @Transactional
    public FeedbackResponse addFeedback(User user, String idStr, boolean isHelpful, String reason) {
        Analysis analysis = getOwned(user, idStr);
        Feedback feedback = feedbackRepository.save(new Feedback(analysis, isHelpful, reason));
        return new FeedbackResponse(str(feedback.getId()));
    }

    // ---------------------------------------------------------------- ownership

    private Analysis getOwned(User user, String idStr) {
        UUID id = parseUuid(idStr);
        Analysis a = analysisRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (a.getUser() == null || !a.getUser().getId().equals(user.getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return a;
    }

    private UUID parseUuid(String idStr) {
        try {
            return UUID.fromString(idStr);
        } catch (Exception e) {
            // UUID 형식이 아니면 존재하지 않는 리소스로 취급
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private AnalysisListResponse readMockList() {
        try (InputStream in = new ClassPathResource("mock/list.json").getInputStream()) {
            return objectMapper.readValue(in, AnalysisListResponse.class);
        } catch (Exception e) {
            log.error("mock list.json 로드 실패", e);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "mock 목록을 불러오지 못했습니다.");
        }
    }

    // ---------------------------------------------------------------- validation

    private byte[] validateImage(String mediaType, String base64) {
        if (mediaType == null || !ALLOWED_MEDIA_TYPES.contains(mediaType.toLowerCase())) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(stripDataUrl(base64));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "image_base64 를 디코딩할 수 없습니다.");
        }
        if (bytes.length > maxImageBytes) {
            throw new ApiException(ErrorCode.IMAGE_TOO_LARGE);
        }
        return bytes;
    }

    private String stripDataUrl(String base64) {
        int comma = base64.indexOf(',');
        if (base64.startsWith("data:") && comma > 0) {
            return base64.substring(comma + 1);
        }
        return base64;
    }

    // ---------------------------------------------------------------- mock mode

    private AnalysisResponse handleMock(User user, OutputMode outputMode, String hint, boolean save) {
        String file = selectMockFile(outputMode, hint);
        AnalysisResponse mock = readMock(file);
        if (mockSave) {
            Analysis saved = analysisRepository.save(fromResponse(user, mock, outputMode, save));
            return toResponse(saved);
        }
        return mock;
    }

    private String selectMockFile(OutputMode outputMode, String hint) {
        if (outputMode == OutputMode.NATIVE) {
            return "mock/analyze_native_wage.json";
        }
        String h = hint == null ? "" : hint.toLowerCase();
        boolean fine = h.contains("과태료") || h.contains("주차") || h.contains("벌금") || h.contains("fine");
        return fine ? "mock/analyze_easy_fine.json" : "mock/analyze_easy_health.json";
    }

    private AnalysisResponse readMock(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, AnalysisResponse.class);
        } catch (Exception e) {
            log.error("mock 파일 로드 실패: {}", path, e);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "mock 데이터를 불러오지 못했습니다.");
        }
    }

    // ---------------------------------------------------------------- real mode

    private AnalysisResponse handleReal(User user, OutputMode outputMode, String lang,
                                        String base64, String mediaType, boolean save) {
        byte[] imageBytes = validateImage(mediaType, base64);

        Analysis analysis = new Analysis();
        analysis.setUser(userRepository.getReferenceById(user.getId()));
        analysis.setOutputMode(outputMode);
        analysis.setLang(lang);
        analysis.setImageRef(null); // 기본 이미지 미저장 정책
        analysis.setImageHash(sha256Hex(imageBytes));
        analysis.setModel(aiClient.getModel());
        analysis.setSaved(save);

        long start = System.currentTimeMillis();
        AiAnalysis parsed = callAiWithRetry(base64, mediaType, outputMode, lang);
        analysis.setLatencyMs((int) (System.currentTimeMillis() - start));

        if (parsed == null) {
            analysis.setStatus(AnalysisStatus.FAILED);
            analysisRepository.save(analysis);
            throw new ApiException(ErrorCode.AI_PARSE_FAILED);
        }

        applyParsed(analysis, parsed, outputMode);
        analysis.setStatus(AnalysisStatus.DONE);
        Analysis saved = analysisRepository.save(analysis);
        return toResponse(saved);
    }

    /** 파싱 실패 시 1회 재시도. HTTP 오류(UPSTREAM/RATE)는 즉시 전파. */
    private AiAnalysis callAiWithRetry(String base64, String mediaType, OutputMode outputMode, String lang) {
        AiParseException last = null;
        for (int attempt = 1; attempt <= MAX_AI_ATTEMPTS; attempt++) {
            String text = null;
            try {
                text = aiClient.complete(base64, mediaType, outputMode, lang);
                return parser.parse(text);
            } catch (AiParseException e) {
                log.warn("AI 파싱 실패 (attempt {}/{}): {} | 응답원문(최대2000자)=[{}]",
                        attempt, MAX_AI_ATTEMPTS, e.getMessage(), snippet(text));
                last = e;
            }
        }
        log.error("AI 파싱 최종 실패", last);
        return null;
    }

    private void applyParsed(Analysis analysis, AiAnalysis p, OutputMode outputMode) {
        analysis.setDocType(p.docType());
        analysis.setSummaryOneLine(p.summaryOneLine());
        analysis.setOriginalText(p.originalText());
        analysis.setExplainedText(p.explainedText());
        analysis.setConsequence(p.consequence());

        if (p.cards() != null) {
            analysis.setCardWhat(p.cards().what());
            analysis.setCardWhen(p.cards().when());
            analysis.setCardWhere(p.cards().where());
            analysis.setCardAmount(p.cards().amount());
            analysis.setCardDeadline(parseDeadline(p.cards().deadline()));
        }

        if (p.risks() != null) {
            for (AiAnalysis.Risk r : p.risks()) {
                analysis.addRisk(new Risk(RiskLevel.fromJson(r.level()), r.type(), r.message()));
            }
        }
        if (p.actions() != null) {
            for (AiAnalysis.Action a : p.actions()) {
                analysis.addAction(new Action(a.text(), a.priority()));
            }
        }
        // reply_drafts 는 native 모드에서만. easy 는 항상 빈 배열.
        if (outputMode == OutputMode.NATIVE && p.replyDrafts() != null) {
            for (AiAnalysis.ReplyDraft d : p.replyDrafts()) {
                analysis.addReplyDraft(new ReplyDraft(d.korean(), d.noteInLang()));
            }
        }
    }

    // ---------------------------------------------------------------- mapping

    /** 저장된 엔티티 → 응답 DTO. created_at 은 UTC 로 변환. */
    public AnalysisResponse toResponse(Analysis a) {
        List<RiskDto> risks = new ArrayList<>();
        for (Risk r : a.getRisks()) {
            risks.add(new RiskDto(str(r.getId()), r.getLevel(), r.getType(), r.getMessage()));
        }
        List<ActionDto> actions = new ArrayList<>();
        for (Action ac : a.getActions()) {
            actions.add(new ActionDto(str(ac.getId()), ac.getText(), ac.getPriority(), ac.isDone()));
        }
        List<ReplyDraftDto> replies = new ArrayList<>();
        for (ReplyDraft d : a.getReplyDrafts()) {
            replies.add(new ReplyDraftDto(str(d.getId()), d.getKorean(), d.getNoteInLang()));
        }
        CardDto cards = new CardDto(a.getCardWhat(), a.getCardWhen(), a.getCardWhere(),
                a.getCardAmount(), a.getCardDeadline());

        return new AnalysisResponse(
                str(a.getId()), a.getStatus(), a.getOutputMode(), a.getLang(), a.getDocType(),
                a.getSummaryOneLine(), a.getOriginalText(), a.getExplainedText(), a.getConsequence(),
                cards, risks, actions, replies, toUtc(a.getCreatedAt()));
    }

    /** 응답 DTO(mock) → 엔티티. id/created_at 은 새로 생성. */
    private Analysis fromResponse(User user, AnalysisResponse r, OutputMode outputMode, boolean save) {
        Analysis a = new Analysis();
        a.setUser(userRepository.getReferenceById(user.getId()));
        a.setStatus(r.status() != null ? r.status() : AnalysisStatus.DONE);
        a.setOutputMode(outputMode);
        a.setLang(r.lang());
        a.setDocType(r.docType());
        a.setSummaryOneLine(r.summaryOneLine());
        a.setOriginalText(r.originalText());
        a.setExplainedText(r.explainedText());
        a.setConsequence(r.consequence());
        a.setModel(aiClient.getModel());
        a.setSaved(save);
        if (r.cards() != null) {
            a.setCardWhat(r.cards().what());
            a.setCardWhen(r.cards().when());
            a.setCardWhere(r.cards().where());
            a.setCardAmount(r.cards().amount());
            a.setCardDeadline(r.cards().deadline());
        }
        if (r.risks() != null) {
            for (RiskDto rd : r.risks()) {
                a.addRisk(new Risk(rd.level(), rd.type(), rd.message()));
            }
        }
        if (r.actions() != null) {
            for (ActionDto ad : r.actions()) {
                a.addAction(new Action(ad.text(), ad.priority()));
            }
        }
        if (r.replyDrafts() != null) {
            for (ReplyDraftDto dd : r.replyDrafts()) {
                a.addReplyDraft(new ReplyDraft(dd.korean(), dd.noteInLang()));
            }
        }
        return a;
    }

    // ---------------------------------------------------------------- helpers

    private LocalDate parseDeadline(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            log.debug("deadline 파싱 실패, null 처리: {}", value);
            return null;
        }
    }

    private static String str(Object id) {
        return id == null ? null : id.toString();
    }

    private static String snippet(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() > 2000 ? s.substring(0, 2000) + "...(truncated)" : s;
    }

    private static OffsetDateTime toUtc(OffsetDateTime t) {
        return t == null ? null : t.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
