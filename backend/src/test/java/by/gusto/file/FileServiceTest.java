package by.gusto.file;

import by.gusto.auth.entity.Role;
import by.gusto.auth.entity.User;
import by.gusto.auth.service.AuthContext;
import by.gusto.common.exception.ErrorCode;
import by.gusto.common.exception.GustoException;
import by.gusto.file.dto.FileResponse;
import by.gusto.file.entity.FileEntity;
import by.gusto.file.repository.FileRepository;
import by.gusto.file.repository.ProductImageRepository;
import by.gusto.file.service.FileService;
import by.gusto.file.service.FileStorageProperties;
import by.gusto.file.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileServiceTest {

    @Mock
    private FileStorageProperties properties;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private AuthContext authContext;

    @InjectMocks
    private FileService fileService;

    private final UUID ownerId = UUID.randomUUID();
    private final String storageKey = UUID.randomUUID().toString();

    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D
    };

    @BeforeEach
    void setUp() {
        when(properties.getMaxFileSize()).thenReturn(10 * 1024 * 1024L);
        when(properties.getAllowedMimeTypes()).thenReturn(List.of("image/jpeg", "image/png", "image/webp"));
    }

    @Test
    void uploadPublicFileSavesEntityAndStorage() throws IOException {
        authenticate(ownerId, Role.ADMIN);
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", PNG_BYTES);
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(fileStorageService).store(any(InputStream.class), any());

        FileResponse response = fileService.upload(file, FileEntity.Visibility.PUBLIC);

        assertThat(response.getOriginalName()).isEqualTo("test.png");
        assertThat(response.getMimeType()).isEqualTo("image/png");
        assertThat(response.getVisibility()).isEqualTo(FileEntity.Visibility.PUBLIC);
        assertThat(response.getUrl()).contains("/api/v1/files/");
        verify(fileStorageService).store(any(InputStream.class), any());
    }

    @Test
    void uploadInvalidMimeTypeRejected() {
        authenticate(ownerId, Role.ADMIN);
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "text".getBytes());

        assertThatThrownBy(() -> fileService.upload(file, FileEntity.Visibility.PUBLIC))
                .isInstanceOf(GustoException.class)
                .satisfies(e -> assertThat(((GustoException) e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void uploadOversizedFileRejected() {
        authenticate(ownerId, Role.ADMIN);
        when(properties.getMaxFileSize()).thenReturn(1L);
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", PNG_BYTES);

        assertThatThrownBy(() -> fileService.upload(file, FileEntity.Visibility.PUBLIC))
                .isInstanceOf(GustoException.class)
                .satisfies(e -> assertThat(((GustoException) e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void anonymousCanDownloadPublicFile() {
        FileEntity file = publicFile();
        when(fileRepository.findByStorageKey(storageKey)).thenReturn(Optional.of(file));
        when(fileStorageService.load(storageKey)).thenReturn(InputStream.nullInputStream());

        assertThat(fileService.download(storageKey)).isNotNull();
    }

    @Test
    void anonymousCannotDownloadPrivateFile() {
        FileEntity file = privateFile();
        when(fileRepository.findByStorageKey(storageKey)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.download(storageKey))
                .isInstanceOf(GustoException.class)
                .satisfies(e -> assertThat(((GustoException) e).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void ownerCanDownloadPrivateFile() {
        FileEntity file = privateFile();
        authenticate(ownerId, Role.CUSTOMER_LEGAL);
        when(fileRepository.findByStorageKey(storageKey)).thenReturn(Optional.of(file));
        when(fileStorageService.load(storageKey)).thenReturn(InputStream.nullInputStream());

        assertThat(fileService.download(storageKey)).isNotNull();
    }

    @Test
    void adminCanDeleteOthersPrivateFile() {
        FileEntity file = privateFile();
        authenticate(UUID.randomUUID(), Role.ADMIN);
        when(fileRepository.findByStorageKey(storageKey)).thenReturn(Optional.of(file));
        when(productImageRepository.existsByFileId(file.getId())).thenReturn(false);
        doNothing().when(fileStorageService).delete(storageKey);

        fileService.delete(storageKey);

        verify(fileStorageService).delete(storageKey);
    }

    @Test
    void cannotDeleteFileReferencedByProductImage() {
        FileEntity file = publicFile();
        authenticate(UUID.randomUUID(), Role.ADMIN);
        when(fileRepository.findByStorageKey(storageKey)).thenReturn(Optional.of(file));
        when(productImageRepository.existsByFileId(file.getId())).thenReturn(true);

        assertThatThrownBy(() -> fileService.delete(storageKey))
                .isInstanceOf(GustoException.class)
                .satisfies(e -> assertThat(((GustoException) e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        verify(fileStorageService, org.mockito.Mockito.never()).delete(storageKey);
    }

    private FileEntity publicFile() {
        return FileEntity.builder()
                .id(UUID.randomUUID())
                .storageKey(storageKey)
                .originalName("public.png")
                .mimeType("image/png")
                .sizeBytes(100L)
                .ownerId(ownerId)
                .visibility(FileEntity.Visibility.PUBLIC)
                .build();
    }

    private FileEntity privateFile() {
        return FileEntity.builder()
                .id(UUID.randomUUID())
                .storageKey(storageKey)
                .originalName("private.png")
                .mimeType("image/png")
                .sizeBytes(100L)
                .ownerId(ownerId)
                .visibility(FileEntity.Visibility.PRIVATE)
                .build();
    }

    private void authenticate(UUID userId, Role role) {
        User user = User.builder().id(userId).email("user@test.by").role(role).build();
        when(authContext.getCurrentUser()).thenReturn(user);

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        Collection<? extends GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        doReturn(authorities).when(authentication).getAuthorities();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
