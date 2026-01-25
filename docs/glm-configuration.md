# GLM (Z.AI) Configuration

TDD Guard supports Z.AI's GLM models as cost-effective alternatives to Anthropic's Claude models. GLM-4.7 provides an Anthropic-compatible API endpoint, making it a drop-in replacement for TDD validation.

## Why Use GLM?

- Significantly lower token costs compared to Claude models
- Suitable for TDD validation where Claude-level reasoning may not be necessary
- Reduces API costs when running frequent validation checks

## Configuration

### Environment Variables

Add these to your `.env` file:

```bash
VALIDATION_CLIENT=api
TDD_GUARD_ANTHROPIC_API_KEY=your_zai_api_key
TDD_GUARD_ANTHROPIC_BASE_URL=https://api.z.ai/api/anthropic
TDD_GUARD_MODEL_VERSION=GLM-4.7
```

### Available Models

| Model       | Use Case                                |
| ----------- | --------------------------------------- |
| GLM-4.7     | Recommended for most validation tasks   |
| GLM-4.5-Air | Lighter model for faster response times |

## Custom Instructions for GLM

GLM models require different prompting techniques than Claude. The default `instructions.md` is optimized for Claude and may not work optimally with GLM.

### Using GLM-Optimized Instructions

Replace the contents of `.claude/tdd-guard/data/instructions.md` with GLM-specific prompts.

A community-contributed GLM-optimized instructions file is available. See the discussion in [Issue #95](https://github.com/nizos/tdd-guard/issues/95) for the latest version.

### Key Differences

GLM prompts typically need:

- More explicit and structured formatting
- Clearer step-by-step instructions
- Different emphasis on validation criteria

## Claude Code Integration

If you're using Claude Code itself with Z.AI as the backend, configure these environment variables in your Claude Code settings (`.claude/settings.json` or `~/.claude/settings.json`):

```json
{
  "env": {
    "ANTHROPIC_AUTH_TOKEN": "your_zai_api_key",
    "ANTHROPIC_BASE_URL": "https://api.z.ai/api/anthropic",
    "API_TIMEOUT_MS": "3000000"
  }
}
```

Model mapping for Claude Code:

- `ANTHROPIC_DEFAULT_OPUS_MODEL`: GLM-4.7
- `ANTHROPIC_DEFAULT_SONNET_MODEL`: GLM-4.7
- `ANTHROPIC_DEFAULT_HAIKU_MODEL`: GLM-4.5-Air

## Troubleshooting

### API Errors

If you receive authentication errors:

1. Verify your Z.AI API key is correct
2. Ensure the base URL is exactly `https://api.z.ai/api/anthropic`
3. Check that `VALIDATION_CLIENT` is set to `api`

### Validation Quality

If GLM validation results seem inconsistent:

1. Use the GLM-optimized `instructions.md` (see above)
2. Consider using GLM-4.7 instead of GLM-4.5-Air for better accuracy
3. Review and adjust the custom instructions for your specific use case

## Contributing

If you've developed improved prompts for GLM, consider contributing them back to the community. See [Issue #95](https://github.com/nizos/tdd-guard/issues/95) for ongoing discussion about GLM-optimized instructions.
