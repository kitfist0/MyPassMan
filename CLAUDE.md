# Project: MyPassMan (`my.passman`)

## Architecture

- MVVM with unidirectional data flow (UI State + Events)
- Jetpack Compose for all new UI — no XML layouts
- Hilt for dependency injection

## Conventions

- ViewModels expose StateFlow<UiState>, never mutable state directly
- Repositories return Flow<Result<T>>, never throw
- All Composables are stateless where possible — hoist state to the caller

## Testing

- ViewModels: JUnit5 + Turbine for Flow testing + MockK
- Compose UI: use createComposeRule(), test by semantics, not implementation
- Minimum 80% coverage on ViewModel and Repository layers

## Build

- Min SDK 24, target SDK 36, Kotlin 2.x, AGP 9.3.2
- Run ./gradlew ktlintCheck detekt before considering a change complete
