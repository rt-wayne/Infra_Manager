package com.mpx.infra_manager_java.service.imports;

// ============================================================
// AI版本  : Claude Fable 5.1 (claude-fable-5-1)
// 修改日期: 2026-10-06
// 變更說明: 新增：匯入 CLI 結束碼測試（S2 回合二匯入器）。
//           缺參數 → 1 且不讀檔不匯入；成功 → 0；UserImportException 與 DB 例外 → 1；exit 以 lambda 攔下結束碼
// ============================================================

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataAccessResourceFailureException;

import com.mpx.infra_manager_java.model.imports.ImportMapping;
import com.mpx.infra_manager_java.model.imports.ImportSummary;
import com.mpx.infra_manager_java.model.imports.LegacyUser;

class UserImportRunnerTest {

	private final UserImportService service = mock(UserImportService.class);
	private final ImportFileReader reader = mock(ImportFileReader.class);
	private final AtomicInteger exitCode = new AtomicInteger(-1);

	@Test
	void 缺任一參數回1且不讀檔() {
		runner("users.json", " ").run(new DefaultApplicationArguments());
		assertThat(exitCode.get()).isEqualTo(1);
		runner("", "map.csv").run(new DefaultApplicationArguments());
		assertThat(exitCode.get()).isEqualTo(1);
		verifyNoInteractions(reader, service);
	}

	@Test
	void 成功回0() {
		List<LegacyUser> users = List.of(new LegacyUser("wayne", "Wayne", null, null, null, null, List.of(), true));
		ImportMapping mapping = new ImportMapping(Map.of("wayne", "T0001"));
		when(reader.readUsers(Path.of("users.json"))).thenReturn(users);
		when(reader.readMapping(Path.of("map.csv"))).thenReturn(mapping);
		when(service.importUsers(users, mapping)).thenReturn(new ImportSummary(1, 0, 1, 0, List.of("alan kuo")));

		runner("users.json", "map.csv").run(new DefaultApplicationArguments());

		assertThat(exitCode.get()).isZero();
	}

	@Test
	void 檢核失敗與DB例外都回1() {
		when(reader.readUsers(any())).thenThrow(new UserImportException(List.of("第 1 筆 id 空白", "x")));
		runner("users.json", "map.csv").run(new DefaultApplicationArguments());
		assertThat(exitCode.get()).isEqualTo(1);

		doReturn(List.of()).when(reader).readUsers(any());
		when(reader.readMapping(any())).thenReturn(new ImportMapping(Map.of()));
		when(service.importUsers(any(), any())).thenThrow(new DataAccessResourceFailureException("boom"));
		runner("users.json", "map.csv").run(new DefaultApplicationArguments());
		assertThat(exitCode.get()).isEqualTo(1);
	}

	private UserImportRunner runner(String users, String mapping) {
		return new UserImportRunner(service, reader, users, mapping, exitCode::set);
	}
}
