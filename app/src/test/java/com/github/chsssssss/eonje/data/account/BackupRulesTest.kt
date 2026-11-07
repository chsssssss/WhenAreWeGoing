package com.github.chsssssss.eonje.data.account

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "정리 대기" 표시 파일(DataStore `account_cleanup`)이 자동 백업에서 빠져 있는지 — 다른 기기에 복원돼서 그 기기엔 없는 파일을 지우려 들면 안 된다.
 * 파일 이름은 코드의 상수에서 만들어서, 이름을 바꾸고 규칙을 안 고치면 이 테스트가 깨진다.
 */
class BackupRulesTest {
    private val excluded = "datastore/${PendingLocalCleanupStore.DATASTORE_NAME}.preferences_pb"

    private fun res(name: String): String {
        // Gradle 단위 테스트의 작업 디렉터리는 모듈(app/)이다.
        val file = listOf("src/main/res/xml/$name", "app/src/main/res/xml/$name").map(::File).first { it.exists() }
        return file.readText()
    }

    @Test
    fun 정리_대기_표시는_자동_백업에서_제외된다() {
        val backup = res("backup_rules.xml")
        val extraction = res("data_extraction_rules.xml")

        assertTrue("backup_rules.xml에 $excluded 제외 규칙", backup.contains("""domain="file" path="$excluded""""))
        assertTrue("data_extraction_rules.xml cloud-backup", extraction.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>").contains(excluded))
        assertTrue("data_extraction_rules.xml device-transfer", extraction.substringAfter("<device-transfer>").substringBefore("</device-transfer>").contains(excluded))
    }
}
