# Test conventions

- Test method names use the English public behavior name in `camelCase`, followed by an underscore-separated outcome and reason.
- `@DisplayName` uses a Korean sentence that describes the behavior.
- Include success/failure and the failure reason in the method name when relevant.
- Every public behavior with a success path has at least one happy case and one bad case; add more bad cases for meaningful validation, authorization, boundary, concurrency, and external failure branches.
- Examples: `authMe_success`, `authMe_failure_unauthorized`.
- Lifecycle methods such as `setUp` and `clearSecurityContext` also use English names.
- 테스트 클래스 선언의 여는 중괄호 다음에는 빈 줄을 하나 둔다. 상수와 인스턴스 필드 사이에는 빈 줄을 하나 두고, 연속된 일반 필드는 붙여 쓴다. 필드 선언은 한 줄에 하나만 작성한다.
- 테스트에서 사용하는 JPA `@Entity` 클래스는 애노테이션이 붙은 필드와 그 필드를 하나의 블록으로 보고, 필드 블록 사이에 빈 줄을 둔다. 애노테이션이 없는 연속 필드는 같은 블록으로 붙여 쓴다.
- Use `// given`, `// when`, and `// then` when the sections are meaningful. Combine inseparable execution and assertion as `// when & then`, remove empty section comments, and keep one logical `when` per test.
- Test classes mirror the corresponding `src/main/java` package. Shared test bootstrap belongs in `testsupport`.
- Repository and persistence tests use Testcontainers PostgreSQL with Flyway migrations; H2 and a production database are not used.
- Mockito is preferred for unit/application collaborators; fake implementations are not introduced unless explicitly decided.
