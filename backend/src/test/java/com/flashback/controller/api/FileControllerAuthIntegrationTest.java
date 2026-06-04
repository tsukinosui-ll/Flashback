package com.flashback.controller.api;

import com.flashback.domain.User;
import com.flashback.domain.UserStatus;
import com.flashback.mapper.UserMapper;
import com.flashback.security.auth.AuthRole;
import com.flashback.security.auth.AuthUser;
import com.flashback.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileControllerAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(4001L);
        user.setStatus(UserStatus.ENABLED);
        when(userMapper.selectById(4001L)).thenReturn(user);
    }

    @TempDir
    static Path tempUploadDir;

    @DynamicPropertySource
    static void configureUploadBaseDir(DynamicPropertyRegistry registry) {
        registry.add("app.upload.base-dir", tempUploadDir::toString);
    }

    private String bearerToken() {
        return "Bearer " + jwtTokenProvider.createToken(new AuthUser(4001L, AuthRole.USER));
    }

    @Nested
    class UploadValidation {

        @Test
        void shouldReturn401WhenNotLoggedIn() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.png", "image/png", fakePngContent());

            mockMvc.perform(multipart("/api/files/upload")
                            .file(file))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldReturn400WhenFileIsEmpty() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.png", "image/png", new byte[0]);

            mockMvc.perform(multipart("/api/files/upload")
                            .file(file)
                            .header("Authorization", bearerToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("文件不能为空"));
        }

        @Test
        void shouldReturn400WhenExtensionNotAllowed() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "malware.exe", "application/octet-stream", "fake content".getBytes());

            mockMvc.perform(multipart("/api/files/upload")
                            .file(file)
                            .header("Authorization", bearerToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不支持的文件类型")));
        }

        @Test
        void shouldReturn400WhenMagicNumberMismatch() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "fake.png", "image/png", "not-a-real-png-content".getBytes());

            mockMvc.perform(multipart("/api/files/upload")
                            .file(file)
                            .header("Authorization", bearerToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("文件内容与扩展名不匹配")));
        }
    }

    @Nested
    class SuccessfulUpload {

        @Test
        void shouldUploadPngSuccessfully() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "screenshot.png", "image/png", fakePngContent());

            mockMvc.perform(multipart("/api/files/upload")
                            .file(file)
                            .header("Authorization", bearerToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.startsWith("/uploads/4001/")));
        }

        @Test
        void shouldUploadJpgSuccessfully() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", fakeJpgContent());

            mockMvc.perform(multipart("/api/files/upload")
                            .file(file)
                            .header("Authorization", bearerToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.startsWith("/uploads/4001/")));
        }

        @Test
        void shouldUploadGifSuccessfully() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "animation.gif", "image/gif", fakeGifContent());

            mockMvc.perform(multipart("/api/files/upload")
                            .file(file)
                            .header("Authorization", bearerToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.startsWith("/uploads/4001/")));
        }

        @Test
        void shouldWriteFileToDisk() throws Exception {
            byte[] content = fakePngContent();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "persist.png", "image/png", content);

            String responseBody = mockMvc.perform(multipart("/api/files/upload")
                            .file(file)
                            .header("Authorization", bearerToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // 从 JSON 响应中提取 relativePath
            String relativePath = responseBody.split("\"data\":\"")[1].split("\"")[0];
            String filename = relativePath.substring(relativePath.lastIndexOf('/') + 1);

            Path writtenFile = tempUploadDir.resolve("4001")
                    .resolve(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")))
                    .resolve(filename);
            assertThat(writtenFile).exists();
            assertThat(Files.readAllBytes(writtenFile)).isEqualTo(content);
        }
    }

    // --- 测试用魔数内容（仅头部有效） ---
    private byte[] fakePngContent() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
                0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, (byte) 0x53, (byte) 0xDE};
    }

    private byte[] fakeJpgContent() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46,
                0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00};
    }

    private byte[] fakeGifContent() {
        return new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
                (byte) 0x80, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                0x00, 0x00, 0x00, 0x21, (byte) 0xF9, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00,
                (byte) 0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x4C, 0x01, 0x00, 0x3B};
    }
}
