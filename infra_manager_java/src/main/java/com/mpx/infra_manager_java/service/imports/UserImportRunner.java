package com.mpx.infra_manager_java.service.imports;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：使用者匯入的 CLI 進入點（S2 回合二匯入器）。
//           同一個後端 jar 以 --spring.main.web-application-type=none --im.import.users=<users.json> --im.import.mapping=<csv> 啟動，
//           不起 Tomcat；只在有 im.import.users 且非 web 模式時才成為 bean（web 服務正常啟動時不存在，不會誤跑）。
//           結束碼 0 成功、1 失敗；失敗原因印進 log（檔案問題、檢核問題逐條列；DB 例外只記類別名與 ORA 碼）。
//           不依賴 com.mpx.Application 的 main（範本檔不改），結束由 SpringApplication.exit + System.exit 完成
// ============================================================

import java.nio.file.Path;
import java.util.List;
import java.util.function.IntConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import com.mpx.infra_manager_java.model.imports.ImportMapping;
import com.mpx.infra_manager_java.model.imports.ImportSummary;
import com.mpx.infra_manager_java.model.imports.LegacyUser;
import com.mpx.infra_manager_java.util.Throwables;

@Component
@ConditionalOnNotWebApplication
@ConditionalOnProperty(name = UserImportRunner.USERS_PROPERTY)
public class UserImportRunner implements ApplicationRunner {

	public static final String USERS_PROPERTY = "im.import.users";
	public static final String MAPPING_PROPERTY = "im.import.mapping";

	private static final Logger log = LoggerFactory.getLogger(UserImportRunner.class);

	private final UserImportService service;
	private final ImportFileReader reader;
	private final String usersPath;
	private final String mappingPath;
	private final IntConsumer exit;

	@Autowired
	public UserImportRunner(UserImportService service, ImportFileReader reader, ConfigurableApplicationContext context,
			@Value("${" + USERS_PROPERTY + "}") String usersPath,
			@Value("${" + MAPPING_PROPERTY + ":}") String mappingPath) {
		this(service, reader, usersPath, mappingPath, code -> System.exit(SpringApplication.exit(context, () -> code)));
	}

	/** 單元測試用：exit 換成記錄結束碼的 lambda */
	UserImportRunner(UserImportService service, ImportFileReader reader, String usersPath, String mappingPath,
			IntConsumer exit) {
		this.service = service;
		this.reader = reader;
		this.usersPath = usersPath == null ? "" : usersPath.trim();
		this.mappingPath = mappingPath == null ? "" : mappingPath.trim();
		this.exit = exit;
	}

	@Override
	public void run(ApplicationArguments args) {
		exit.accept(execute());
	}

	/** 回結束碼：0 成功、1 失敗 */
	int execute() {
		if (usersPath.isEmpty() || mappingPath.isEmpty()) {
			log.error("匯入失敗：必須同時指定 --{}=<users.json> 與 --{}=<對照表 csv>", USERS_PROPERTY, MAPPING_PROPERTY);
			return 1;
		}
		try {
			List<LegacyUser> users = reader.readUsers(Path.of(usersPath));
			ImportMapping mapping = reader.readMapping(Path.of(mappingPath));
			log.info("匯入開始 users={} mapping={}", users.size(), mapping.size());
			ImportSummary summary = service.importUsers(users, mapping);
			log.info("匯入完成 新增={} 更新={} 角色新增/啟用={} 角色停用={} 略過={}", summary.inserted(), summary.updated(),
					summary.rolesAdded(), summary.rolesDisabled(), summary.skipped().size());
			if (!summary.skipped().isEmpty()) {
				log.warn("對照表沒有、未匯入的帳號：{}", String.join(", ", summary.skipped()));
			}
			log.info("全員密碼已重設為帳號小寫（預設密碼），首次登入須改密碼");
			return 0;
		} catch (UserImportException e) {
			log.error("匯入失敗，共 {} 個問題，一筆都未寫入：", e.getProblems().size());
			for (String problem : e.getProblems()) {
				log.error("  - {}", problem);
			}
			return 1;
		} catch (RuntimeException e) {
			log.error("匯入失敗（{}），一筆都未寫入", Throwables.summarize(e));
			return 1;
		}
	}
}
