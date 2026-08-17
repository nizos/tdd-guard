import { describe, it, expect } from 'vitest'
import { countTestDefinitions } from './testCounter'

describe('countTestDefinitions', () => {
  describe('javascript', () => {
    it('should count a single test() call', () => {
      const code = `test('should add numbers', () => {
        expect(add(1, 2)).toBe(3)
      })`
      expect(countTestDefinitions(code, 'javascript')).toBe(1)
    })

    it('should count a single it() call', () => {
      const code = `it('should add numbers', () => {
        expect(add(1, 2)).toBe(3)
      })`
      expect(countTestDefinitions(code, 'javascript')).toBe(1)
    })

    it('should count multiple test definitions', () => {
      const code = `
        test('first', () => { expect(1).toBe(1) })
        test('second', () => { expect(2).toBe(2) })
      `
      expect(countTestDefinitions(code, 'javascript')).toBe(2)
    })

    it('should count zero when no tests present', () => {
      const code = `function add(a, b) { return a + b }`
      expect(countTestDefinitions(code, 'javascript')).toBe(0)
    })

    it('should count it.each() as a test', () => {
      const code = `it.each([1, 2, 3])('should handle %i', (n) => {
        expect(n).toBeGreaterThan(0)
      })`
      expect(countTestDefinitions(code, 'javascript')).toBe(1)
    })

    it('should count test.skip() as a test', () => {
      const code = `test.skip('should be skipped', () => {
        expect(true).toBe(false)
      })`
      expect(countTestDefinitions(code, 'javascript')).toBe(1)
    })
  })

  describe('typescript', () => {
    it('should count tests in TypeScript code', () => {
      const code = `test('should handle typed input', () => {
        const result: number = add(1, 2)
        expect(result).toBe(3)
      })`
      expect(countTestDefinitions(code, 'typescript')).toBe(1)
    })
  })

  describe('tsx', () => {
    it('should count each test in a file that renders JSX', () => {
      const code = `it('renders the label', () => {
        render(<Button label="Save" />)
      })
      it('renders children', () => {
        render(<Button label="Save"><span>body</span></Button>)
      })`
      expect(countTestDefinitions(code, 'tsx')).toBe(2)
    })

    it('should count a test that renders a generic component', () => {
      const code = `it('renders a typed list', () => {
        render(<List<string> items={['a']} />)
      })`
      expect(countTestDefinitions(code, 'tsx')).toBe(1)
    })
  })

  describe('python', () => {
    it('should count a single pytest function', () => {
      const code = `def test_addition():
    assert add(1, 2) == 3`
      expect(countTestDefinitions(code, 'python')).toBe(1)
    })

    it('should count multiple pytest functions', () => {
      const code = `def test_addition():
    assert add(1, 2) == 3

def test_subtraction():
    assert subtract(3, 1) == 2`
      expect(countTestDefinitions(code, 'python')).toBe(2)
    })

    it('should count async test functions', () => {
      const code = `async def test_async_fetch():
    result = await fetch_data()
    assert result is not None`
      expect(countTestDefinitions(code, 'python')).toBe(1)
    })

    it('should not count non-test functions', () => {
      const code = `def add(a, b):
    return a + b

def helper_test():
    pass`
      expect(countTestDefinitions(code, 'python')).toBe(0)
    })
  })

  describe('go', () => {
    it('should count a single Go test function', () => {
      const code = `func TestAddition(t *testing.T) {
  result := Add(1, 2)
  if result != 3 {
    t.Errorf("expected 3, got %d", result)
  }
}`
      expect(countTestDefinitions(code, 'go')).toBe(1)
    })

    it('should not count non-test functions', () => {
      const code = `func Add(a, b int) int {
  return a + b
}`
      expect(countTestDefinitions(code, 'go')).toBe(0)
    })

    it('should count benchmark functions', () => {
      const code = `func BenchmarkAdd(b *testing.B) {
  for i := 0; i < b.N; i++ {
    Add(1, 2)
  }
}`
      expect(countTestDefinitions(code, 'go')).toBe(1)
    })
  })

  describe('php', () => {
    it('should count a PHPUnit test method by naming convention', () => {
      const code = `public function testAddition(): void
{
    $this->assertEquals(3, $this->calculator->add(1, 2));
}`
      expect(countTestDefinitions(code, 'php')).toBe(1)
    })

    it('should count a PHPUnit test method with #[Test] attribute', () => {
      const code = `#[Test]
public function addition(): void
{
    $this->assertEquals(3, $this->calculator->add(1, 2));
}`
      expect(countTestDefinitions(code, 'php')).toBe(1)
    })

    it('should not double-count a test method that has both #[Test] and test prefix', () => {
      const code = `#[Test]
public function testAddition(): void
{
    $this->assertEquals(3, $this->calculator->add(1, 2));
}`
      expect(countTestDefinitions(code, 'php')).toBe(1)
    })

    it('should not count non-test methods', () => {
      const code = `public function add(int $a, int $b): int
{
    return $a + $b;
}`
      expect(countTestDefinitions(code, 'php')).toBe(0)
    })

    it('should count multiple test methods', () => {
      const code = `public function testAddition(): void
{
    $this->assertEquals(3, $this->calculator->add(1, 2));
}

public function testSubtraction(): void
{
    $this->assertEquals(1, $this->calculator->subtract(3, 2));
}`
      expect(countTestDefinitions(code, 'php')).toBe(2)
    })

    it('should count mixed naming convention and attribute tests', () => {
      const code = `public function testAddition(): void
{
    $this->assertEquals(3, $this->calculator->add(1, 2));
}

#[Test]
public function subtraction(): void
{
    $this->assertEquals(1, $this->calculator->subtract(3, 2));
}`
      expect(countTestDefinitions(code, 'php')).toBe(2)
    })
  })

  describe('ruby', () => {
    it('should count a single RSpec it block', () => {
      const code = `it 'should add numbers' do
  expect(add(1, 2)).to eq(3)
end`
      expect(countTestDefinitions(code, 'ruby')).toBe(1)
    })

    it('should count multiple it blocks', () => {
      const code = `it 'adds numbers' do
  expect(add(1, 2)).to eq(3)
end

it 'subtracts numbers' do
  expect(subtract(3, 1)).to eq(2)
end`
      expect(countTestDefinitions(code, 'ruby')).toBe(2)
    })

    it('should not count describe blocks as tests', () => {
      const code = `describe Calculator do
end`
      expect(countTestDefinitions(code, 'ruby')).toBe(0)
    })
  })

  describe('rust', () => {
    it('should count a function with #[test] attribute', () => {
      const code = `#[test]
fn test_addition() {
    assert_eq!(add(1, 2), 3);
}`
      expect(countTestDefinitions(code, 'rust')).toBe(1)
    })

    it('should not count functions without #[test]', () => {
      const code = `fn add(a: i32, b: i32) -> i32 {
    a + b
}`
      expect(countTestDefinitions(code, 'rust')).toBe(0)
    })

    it('should count multiple test functions', () => {
      const code = `#[test]
fn test_addition() {
    assert_eq!(add(1, 2), 3);
}

#[test]
fn test_subtraction() {
    assert_eq!(subtract(3, 1), 2);
}`
      expect(countTestDefinitions(code, 'rust')).toBe(2)
    })
  })
})
