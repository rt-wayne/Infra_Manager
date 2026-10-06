package com.mpx.infra_manager_java.service.sysparam;

// ============================================================
// AI版本  : Claude Opus 5.5 (claude-opus-5-5)
// 修改日期: 2026-10-06
// 變更說明: 新增：系統參數讀取（S6 回合二 a，BACKLOG 第 6 項 B3）。每次呼叫都查 DB（PRD：上傳限制每次請求讀取），
//           讀不到、空白或格式不合時退回 PRD 預設值並寫 warn（只記參數名，不記值）。
//           UPLOAD_MAX_MB 只能調低：後端 multipart 與殼 jar 上限寫死 50 MB，設得更大也以 50 為準（S6 分析 Z1）
// ============================================================

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mpx.infra_manager_java.dao.sysparam.SysParamDao;

@Service
public class SysParamService {

	private static final Logger log = LoggerFactory.getLogger(SysParamService.class);

	public static final String UPLOAD_MAX_MB = "UPLOAD_MAX_MB";
	public static final String UPLOAD_MAX_FILES = "UPLOAD_MAX_FILES";
	public static final String FLOW_POLICY = "FLOW_POLICY";

	public static final int HARD_MAX_MB = 50;
	public static final int DEFAULT_MAX_FILES = 30;
	public static final String POLICY_FULL_ONLY = "full_only";
	public static final String POLICY_BY_PRIORITY = "by_priority";

	private static final Set<String> POLICIES = Set.of(POLICY_FULL_ONLY, POLICY_BY_PRIORITY);

	private final SysParamDao sysParamDao;

	public SysParamService(SysParamDao sysParamDao) {
		this.sysParamDao = sysParamDao;
	}

	public int uploadMaxMb() {
		return Math.min(positiveInt(UPLOAD_MAX_MB, HARD_MAX_MB), HARD_MAX_MB);
	}

	public int uploadMaxFiles() {
		return positiveInt(UPLOAD_MAX_FILES, DEFAULT_MAX_FILES);
	}

	public String flowPolicy() {
		String value = first(FLOW_POLICY);
		if (value == null) {
			return POLICY_FULL_ONLY;
		}
		if (!POLICIES.contains(value)) {
			log.warn("系統參數格式不合，改用預設值 param={}", FLOW_POLICY);
			return POLICY_FULL_ONLY;
		}
		return value;
	}

	private int positiveInt(String name, int defaultValue) {
		String value = first(name);
		if (value == null) {
			return defaultValue;
		}
		try {
			int n = Integer.parseInt(value);
			if (n > 0) {
				return n;
			}
		} catch (NumberFormatException e) {
			// 落到下方 warn
		}
		log.warn("系統參數格式不合，改用預設值 param={}", name);
		return defaultValue;
	}

	private String first(String name) {
		List<String> values = sysParamDao.findValues(name);
		if (values.isEmpty() || values.get(0) == null || values.get(0).isBlank()) {
			log.warn("系統參數未設定，改用預設值 param={}", name);
			return null;
		}
		return values.get(0).trim();
	}
}
