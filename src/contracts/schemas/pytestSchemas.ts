import { z } from 'zod'
import { TestModuleSchema } from './vitestSchemas'

// Pytest uses same structure as Vitest
export const PytestResultSchema = z.object({
  testModules: z.array(TestModuleSchema),
})
