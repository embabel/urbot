# Bot Packages

This directory contains bot-specific implementations under `com.embabel.bot.<botname>`.

The `com.embabel.bot` package is included in the component scan via
`@SpringBootApplication(scanBasePackages = ...)` in `UrbotApplication`.

## Extension Mechanism

Each bot profile can customize urbot along three axes:

1. **Properties** — override any `urbot.*` property (persona, objective, LLM, etc.)
2. **Templates** — provide custom Jinja persona, objective, and behaviour templates
3. **Beans** — define tools, `LlmReference`s, or any Spring beans

### 1. Profile-Gated Configuration

Each bot **must** gate its `@Configuration` class with a Spring `@Profile` so that
its beans are only loaded when that profile is active:

```java
package com.embabel.bot.astrid;

@Configuration
@Profile("astrid")
public class AstridConfiguration {
    // Define tools, LlmReferences, or any beans
}
```

Activate via `spring.profiles.active`:

```yaml
spring:
  profiles:
    active: astrid
```

Or multiple: `spring.profiles.active=astrid,otherbot`

### 2. Property Overrides

Create `application-<profile>.properties` (or `.yml`) in `src/main/resources` to
override any `urbot.*` property when the profile is active. Spring Boot merges
profile-specific properties on top of the base `application.yml`.

Example `application-astrid.properties`:

```properties
urbot.persona=astrid
urbot.objective=astrid
urbot.max-words=50
urbot.chat-llm.temperature=1.38
```

This changes the persona template, objective template, response length, and LLM
temperature — all without touching Java code.

Key `urbot.*` properties:

| Property | Description | Default |
|----------|-------------|---------|
| `urbot.persona` | Persona template name (resolves to `prompts/personas/<name>.jinja`) | `assistant` |
| `urbot.objective` | Objective template name (resolves to `prompts/objectives/<name>.jinja`) | `qa` |
| `urbot.behaviour` | Behaviour template name (resolves to `prompts/behaviours/<name>.jinja`) | `default` |
| `urbot.max-words` | Soft word limit for responses | `80` |
| `urbot.chat-llm.model` | LLM model ID | `gpt-4.1-mini` |
| `urbot.chat-llm.temperature` | LLM temperature | `0.0` |
| `urbot.memory.enabled` | Enable memory/proposition extraction | `true` |

### 3. Custom Templates

Add Jinja templates to `src/main/resources/prompts/` to define the bot's voice
and goals. The template names must match the property values.

For a bot named "astrid" with `urbot.persona=astrid` and `urbot.objective=astrid`:

```
src/main/resources/
  prompts/
    personas/astrid.jinja      # "Your voice" — personality, tone, style
    objectives/astrid.jinja    # "Your objectives" — what the bot should do
    behaviours/astrid.jinja    # "Your behaviour" — optional, use "default" to skip
```

Templates have access to `properties` (UrbotProperties) and `user` (UrbotUser)
via the Jinja context. They can also include shared elements:

```jinja
You are Astrid, a warm and insightful astrologer.

{% include "elements/thorough_memory" %}
```

### 4. Tools and References

Define any `Tool` or `LlmReference` beans in your profile configuration.
These are picked up by `ChatActions` when wired into the prompt runner.

```java
@Configuration
@Profile("astrid")
public class AstridConfiguration {

    @Bean
    public AstrologyTools astrologyTools() {
        return new AstrologyTools();
    }
}
```

`LlmReference` beans are particularly powerful — they inject content into the
system prompt (via `contribution()`) and expose tools (via `tools()`). For example,
DICE's `Memory` class is an `LlmReference` that surfaces key memories in the prompt
and provides a search tool for additional retrieval.

## Complete Example: Astrid

**File structure:**

```
src/main/
  java/com/embabel/bot/astrid/
    AstridConfiguration.java
  resources/
    application-astrid.properties
    prompts/
      personas/astrid.jinja
      objectives/astrid.jinja
```

**`AstridConfiguration.java`:**

```java
package com.embabel.bot.astrid;

@Configuration
@Profile("astrid")
public class AstridConfiguration {

    @Bean
    public AstrologyTools astrologyTools() {
        return new AstrologyTools();
    }
}
```

**`application-astrid.properties`:**

```properties
urbot.persona=astrid
urbot.objective=astrid
urbot.max-words=50
urbot.chat-llm.temperature=1.38
```

**`prompts/personas/astrid.jinja`:**

```jinja
You are Astrid, a warm and insightful astrologer.
You speak with gentle authority about celestial matters.

{% include "dice/thorough_memory" %}
```

**`prompts/objectives/astrid.jinja`:**

```jinja
Help the user explore astrological insights about themselves
and the world around them. Use their birth details from memory
to provide personalized readings.
```

**Activate:**

```yaml
spring:
  profiles:
    active: astrid
```
