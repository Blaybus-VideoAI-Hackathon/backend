package com.example.hdb;

import com.example.hdb.repository.ProjectRepository;
import com.example.hdb.repository.SceneRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
class DatabaseConnectionTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private SceneRepository sceneRepository;

    @Test
    void testProjectRepositoryConnection() {
        // Repository 빈이 정상적으로 주입되는지 확인
        assertNotNull(projectRepository, "ProjectRepository should be injected");
        
        // DB 연결 테스트 (count 쿼리 실행)
        long projectCount = projectRepository.count();
        log.info("Project count: {}", projectCount);
        
        // count는 0 이상이어야 함 (음수일 수 없음)
        assertTrue(projectCount >= 0, "Project count should be non-negative");
    }

    @Test
    void testSceneRepositoryConnection() {
        // Repository 빈이 정상적으로 주입되는지 확인
        assertNotNull(sceneRepository, "SceneRepository should be injected");
        
        // DB 연결 테스트 (count 쿼리 실행)
        long sceneCount = sceneRepository.count();
        log.info("Scene count: {}", sceneCount);
        
        // count는 0 이상이어야 함 (음수일 수 없음)
        assertTrue(sceneCount >= 0, "Scene count should be non-negative");
    }

    @Test
    void testDatabaseConnection() {
        // 두 Repository 모두 정상적으로 동작하는지 확인
        assertDoesNotThrow(() -> {
            long projectCount = projectRepository.count();
            long sceneCount = sceneRepository.count();
            
            log.info("Database connection test - Projects: {}, Scenes: {}", projectCount, sceneCount);
        });
    }
}
