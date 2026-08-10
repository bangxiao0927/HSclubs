// @vitest-environment node
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterAll, afterEach, describe, expect, it } from 'vitest'
import type { Plugin } from 'vite'

import { schoolBrandTitlePlugin } from './vite.config'

type ConfigResolvedHook = (config: { mode: string; envDir: string }) => void
type TransformIndexHtmlHook = (html: string) => string

const baseHtml = '<html><head><title></title></head><body></body></html>'
const emptyEnvDir = mkdtempSync(join(tmpdir(), 'hsclubs-vite-config-'))

const runPlugin = (envOverrides: Record<string, string | undefined>) => {
  const plugin: Plugin = schoolBrandTitlePlugin()
  const configResolved = plugin.configResolved as ConfigResolvedHook
  const transformIndexHtml = plugin.transformIndexHtml as TransformIndexHtmlHook

  for (const [key, value] of Object.entries(envOverrides)) {
    if (value === undefined) {
      delete process.env[key]
    } else {
      process.env[key] = value
    }
  }

  configResolved({ mode: 'production', envDir: emptyEnvDir })
  return transformIndexHtml(`${baseHtml}<script>var theme = '__DEFAULT_COLOR_MODE__'</script>`)
}

describe('schoolBrandTitlePlugin default color mode injection', () => {
  const originalEnv = process.env.VITE_DEFAULT_COLOR_MODE

  afterEach(() => {
    if (originalEnv === undefined) {
      delete process.env.VITE_DEFAULT_COLOR_MODE
    } else {
      process.env.VITE_DEFAULT_COLOR_MODE = originalEnv
    }
  })

  afterAll(() => {
    rmSync(emptyEnvDir, { recursive: true, force: true })
  })

  it('falls back to light when VITE_DEFAULT_COLOR_MODE is unset', () => {
    const html = runPlugin({ VITE_DEFAULT_COLOR_MODE: undefined })
    expect(html).toContain("var theme = 'light'")
  })

  it('injects dark when VITE_DEFAULT_COLOR_MODE=dark', () => {
    const html = runPlugin({ VITE_DEFAULT_COLOR_MODE: 'dark' })
    expect(html).toContain("var theme = 'dark'")
  })

  it('falls back to light for any other configured value, matching schoolTemplate', () => {
    const html = runPlugin({ VITE_DEFAULT_COLOR_MODE: 'system' })
    expect(html).toContain("var theme = 'light'")
  })
})
