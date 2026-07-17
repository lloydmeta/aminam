# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Aminam is a Quarkus (Java 25) IAM service: signup, JWT authentication, and an ABAC authorisation
engine over organisations and the databases they own.  `README.md` covers the domain intent and has
mermaid diagrams of the decision flow and the ER model.  Read it first; this file covers the
mechanics and the conventions that are not visible from a single file.

## Commands

```sh
make dev          # ./gradlew quarkusDev; Dev Services start postgres + redis. Swagger at :8080/q/swagger-ui
make test         # ./gradlew test
make keys build up  # generate RSA keypair into ./keys, build JVM image, start the compose stack
make down         # stop the stack, remove volumes
make help         # list targets
```

Running one test or one package:

```sh
./gradlew test --tests 'com.beachape.aminam.domain.authz.services.DefaultPolicyEngineTest'
./gradlew test --tests 'com.beachape.aminam.domain.*'     # unit tests only
```

`./gradlew spotlessApply` before committing; `spotlessCheck` is wired into `build` and fails on
unformatted code.

**Docker is required for `./gradlew test`.**  Unit and integration tests share one source set with no
`@Tag`s and no separate task, so the plain `test` task always starts Dev Services containers.
Selecting unit tests only means `--tests`, as above.

## Layering

`domain` -> `infra` -> `app`, enforced by convention and review only (there is no ArchUnit test).

* **domain**: pure model records, service logic, and ports declared as interfaces.  Never imports
  `infra` or `jakarta.persistence`.
* **infra**: implements the domain ports with Hibernate/Panache, Redis, and CEL.  Impls are
  package-private `@ApplicationScoped` classes; CDI resolves them implicitly by interface, so there
  is no producer method per repository.
* **app**: HTTP only, under `app/routes/v1/`.

`app/wiring/ServiceProducers` is the only producer class and holds exactly one thing: `Clock`.
Inject `Clock` and never call `Instant.now()`.

Each feature (`authc`, `authz`, `orgs`, `databases`) repeats this triad across all three layers.

### Repository and entity convention

Per feature, three classes with fixed names:

* **`<X>Repository`** (domain): plain interface over domain records.  `@Nullable` return means "not
  found" on reads; writes throw `EntityNotFoundException`.
* **`Hibernate<X>Repository`** (infra): package-private, `@ApplicationScoped`, every method
  `@Transactional`.
* **`Panache<X>Repository`** (infra): `implements PanacheRepositoryBase<XEntity, UUID>`, the
  repository pattern rather than active record, holding only custom queries.

Entity/domain mapping lives in a `public static final class Mapper` nested inside the entity, with
`toDomain`, `toEntity`, and `applyChanges`.  `applyChanges` mutates only mutable columns and leans on
Hibernate dirty checking.  No separate mapper class, no MapStruct.

Domain IDs are wrapper records (`DatabaseId`, `OrgId`, `UserId`, `MembershipId`, `TokenId`).  Infra
and JSON unwrap to bare `UUID` at the boundary so a domain rename never touches stored data.
`PolicyId` is the exception: a sealed interface permitting `SystemPolicyId(String)` and
`CustomPolicyId(UUID)`, since a policy id is either a reserved string or a UUID.

## Authorisation engine

The load-bearing part of the codebase.  Two phases, and the split is the point:

* **Gather (all I/O)**: `domain/authz/services/AuthorisationService`.  Resolves resource facts,
  loads the policies attached to the principal and to the resource, resolves each `PolicyId` to a
  `PolicyDocument`, and freezes everything into an `EvaluationContext`.
* **Evaluate (pure, no I/O)**: `domain/authz/services/DefaultPolicyEngine` implementing the
  `PolicyEngine` functional interface.  Reads only the context.  This is what makes the engine unit
  testable and swappable.

Regime is derived, never stored: if the principal's active org owns the resource it is `INTERNAL`
(principal ALLOW **or** resource ALLOW), otherwise `CROSS_ORG` (principal ALLOW **and** resource
ALLOW, i.e. bilateral consent).  A matched DENY on either side wins regardless of regime.  No match
is DENY.

**Fails closed at every hop**, deliberately: unresolvable policy id is skipped and grants nothing;
missing resource yields `Deny("resource not found")` rather than an exception; a CEL error evaluates
to false; an org-less session has a null active org, so it classifies as `CROSS_ORG` and can never
match a resource policy.  Preserve this when touching the engine.

### Fact sources

Authz must not import other features' repositories.  The seam is
`domain/authz/repositories/ResourceFactSource` (`Set<ResourceType> types()` plus
`@Nullable ResourceFacts resolve(UUID)`), implemented once per owning feature in that feature's own
`domain/<feature>/services/`: `DatabaseFactSource`, `OrgFactSource`, `MembershipFactSource` (covers
`MEMBERSHIP` and `SELF_MEMBERSHIP`), `PolicyFactSource`.  `AuthorisationService` builds an
`EnumMap<ResourceType, ResourceFactSource>` from `@Any Instance<ResourceFactSource>` at construction
and throws on a duplicate registration.  **Adding a governed resource type means adding a fact
source, not editing authz.**

### System roles vs custom policies

System roles (`VIEWER`, `ADMIN`, `MANAGER`) are code, not rows: a static catalogue in
`domain/authz/services/SystemPolicies`.  Custom policies are org-owned data in `Policy`.
`AuthorisationService.resolvePolicy` pattern-matches `PolicyId` to unify the two.  "Assigning a role"
is just attaching a `system:*` policy to a membership.

### CEL conditions

Compiled twice against the same `infra/authz/services/CelPolicyEnvironment`: at author time by
`CelPolicyValidator` (reports all failures at once via `InvalidPolicyException`) and at check time by
`CelConditionEvaluator` (any exception becomes false).  The activation is built only from
server-resolved facts, so a request cannot forge an attribute.  `principal` and `resource` are open
`map(string, string)` roots, so a fact source can add keys with no schema change.  No time functions
are declared, keeping conditions deterministic.  The attribute vocabulary is centralised in
`domain/authz/models/ConditionAttributes`.

### Enforcement: the guard builder

`AuthorisationService.guard(actor)` is a sealed type-state builder and the front door for
enforcement.  `visible(ref, notFoundType)` declares a 404 gate, `permit(ref, verb)` a 403 gate:

```java
authz.guard(actor)
    .visible(ref, NotFoundException.Type.DATABASE)   // denied READ  -> 404
    .permit(ref, UPDATE)                             // denied UPDATE -> 403
    .fetch(() -> databases.findById(id));            // null (concurrent delete) -> 404
```

All gates run in one gather, and **every visibility is decided before any permit**, so a 403 can
never surface ahead of an unmet 404 and the API never confirms a resource the caller may not see.
The builder is copy-on-write, which is what keeps the type-state sound under aliasing (hence the
`@SuppressWarnings("BuilderReturnThis")`).

`ResourceRef` is sealed: `Existing(type, id)` or `ToCreate(type, owningOrg)`.  A `ToCreate` has no
row, so only a wildcard pattern can match it, and its owning org comes from the request path.

### Resource visibility and the editable flag

Read and write are decided together.  `DatabaseService.get` issues a batch `authz.checkAll` of
`READ` + `UPDATE`: a denied read is a 404, and the update decision becomes
`VisibleDatabase(database, editable)`.  `list` builds `rows.size() * 2` checks and filters
non-readable rows out entirely.  Where editability is already implied (create, update), the resource
passes `/* editable= */ true`.

Resource-attached policies are currently DATABASE-only (`resourcePoliciesFor` returns `List.of()`
otherwise), so `INTERNAL` reduces to the principal side for every other type.

## Errors

Errors are checked exceptions, not sealed interfaces.  All extend
`domain/errors/DomainException`, which passes `writableStackTrace=false`: these model expected,
handled outcomes, not bugs.

`NotFoundException` (carrying a `Type` enum) and `NotAuthorisedException` are shared in
`domain/errors/`.  Feature-specific errors are nested inside the interface or service that throws
them (`AuthenticationService.InvalidCredentialsException`, `PolicyId.MalformedPolicyIdException`,
`UserRepository.DuplicateUsernameException`).

HTTP mapping is one `@Provider ExceptionMapper<E>` per exception class, in an `app/**/errors/`
package mirroring the domain one, returning `app/models/ErrorResponse`.  **Resources declare
`throws` and let the mapper translate**: no try/catch, no manual `Response.status`.  Every mapper has
a unit test under `src/test/java/com/beachape/aminam/app/**/errors/`.

## REST layer

Routing is by subresource locator.  `app/routes/v1/V1Resource` is the only class with a fixed
`@Path("/api/v1")`; it injects each sub-resource and mounts it from an annotated method.
Sub-resources carry no class-level `@Path`.  Nesting composes, so `OrgDatabasesResource` reads the
parent's `@PathParam("id")`.

Resource shape is `@ApplicationScoped @RunOnVirtualThread @Authenticated`, with field injection
(`@Inject`).  Services use constructor injection on a package-private constructor.  The principal
comes from `Principals.requireUser(security)` and is passed to the service as the first argument.

DTOs are records under `routes/v1/<feature>/models/`.  Requests carry Bean Validation and are taken
with `@Valid`.  Responses expose static factory converters (`DatabaseResponse.from(VisibleDatabase)`,
`DatabaseResponse.of(Database, boolean)`): **domain to DTO conversion lives on the DTO**, never in
the resource body or the service.  List responses wrap in a `values` field.  A `201` needs both
`@ResponseStatus(201)` and `@APIResponse(responseCode = "201")` (the latter for OpenAPI).

## Persistence and config

`quarkus.flyway.migrate-at-start=true` with
`quarkus.hibernate-orm.schema-management.strategy=validate`: **Flyway owns the schema and Hibernate
only validates against it**.  A schema change is a new `src/main/resources/db/migration/V<n>__snake_case.sql`,
never an entity annotation alone.  `integration/infra/SchemaMigrationTest` guards this.

Migration conventions: `UUID PRIMARY KEY` generated by the app (`randomUUID()`, no DB default),
`TIMESTAMPTZ NOT NULL DEFAULT now()`, `VARCHAR(255)` matching the `@Size(max = 255)` on the DTO,
JSONB for policy documents, and a named index per owning-org read path.

`application.properties` uses `%prod.` / `%dev.` / `%test.` prefixes; custom keys are namespaced
`aminam.*`.  Dev and test get Postgres and Redis from Dev Services implicitly (there is no
`src/test/resources` and no `%test` datasource config).  With no JWT key locations configured the app
generates an ephemeral RSA keypair at startup and warns.

## Build constraints that will bite

* **NullAway is an `error`**, with `AnnotatedPackages=com.beachape.aminam`.  The package is
  null-marked by config rather than `package-info.java`, so annotate with
  `org.jspecify.annotations.Nullable` explicitly.
* **`-Werror` on main sources** (dropped for tests), plus Error Prone with
  `allDisabledChecksAsWarnings` and Picnic error-prone-support.  `IdentifierName`,
  `CatchingUnchecked`, and `NonFinalStaticField` are disabled for tests only.
* **Doc comments use the Java 23 markdown form (`/// ...`)**, not `/** */`.  This is uniform: 54
  files, zero exceptions.
* Quarkus and the BOM version are inlined in `build.gradle.kts` for Dependabot.  `protobuf-java` is
  force-resolved to 4.33.5 because dev.cel's gencode outruns the BOM's pin.

## Testing

* **Unit** (`domain/`, `infra/`, `app/`): plain JUnit 5 and Mockito, collaborators built with `new`.
  No Quarkus, no Docker.
* **Integration** (`integration/`, mirroring the main tree beneath itself): `@QuarkusTest` on every
  class, real CDI beans via `@Inject` of the domain interface, or REST-Assured over HTTP.

Conventions, applied without exception across all 61 test classes:

* Test classes are `final` and package-private, named `<Subject>Test`, in a package mirroring the
  subject's.
* No `@DisplayName`, no `@Nested`, no `@Tag`.  Method names are lowerCamelCase behavioural sentences
  (`viewerReadsButCannotEdit`, `findByIdReturnsNullWhenAbsent`), with the expected status appended
  bare when that is the point (`createWithBlankNameReturns400`).
* AssertJ everywhere; Hamcrest only inside REST-Assured `.body(...)`.  Never JUnit `assertEquals`.
  The exception idiom asserts the typed field, not the message string:
  `assertThatExceptionOfType(X.class).isThrownBy(...).satisfies(e -> assertThat(e.type())...)`.
* Fixtures go at the bottom of the class as `private static` factories plus a `private record`.

`integration/utils/TestHelpers` wraps the signup, login, org, and database lifecycle in REST-Assured
calls that assert their own status codes and return typed domain values.  Reuse it rather than
hand-rolling auth.  There is no `@TestSecurity` or mocked identity anywhere: integration tests get a
real signed JWT by really signing up and logging in, and the active org is a claim in the token
obtained via `switchOrgAs`.  `signUp()` generates a collision-free username so tests share one
database without cleanup.

`domain/authz/services/GuardStub` sits deliberately in the production package to reach
`AuthorisationService`'s package-private constructor.  It runs the real `guard()` while replacing
`checkAll` with a decision table (unstubbed defaults to `Deny`).

Tests that document intent well: `DefaultPolicyEngineTest`, `SystemPolicyEnforcementTest`, and the
`integration/authz/` suite (`CrossOrgAuthzTest`, `CelConditionAuthzTest`, `UserScenarioTest`).
