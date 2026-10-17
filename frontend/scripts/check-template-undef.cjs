/**
 * 模板未定义变量检查器：
 * 原理——Vue 3 模板编译后，凡是在 <script setup> 里没有定义的标识符，
 * 在渲染函数中都会以 _ctx.xxx 的形式出现（这正是 App.vue 的 health bug 形态）。
 * 做法：编译每个 .vue 的模板 → 解析渲染函数收集 _ctx.* 引用 → 与 script 顶层定义做差集。
 * 差集为空 = 没有「模板用了但没定义」的变量。
 */
const fs = require('fs')
const path = require('path')
const { parse, compileTemplate } = require('@vue/compiler-sfc')
const { parse: babelParse } = require('@babel/parser')

const SRC = path.resolve(__dirname, '../src')

function walk(dir, out = []) {
  for (const f of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, f.name)
    if (f.isDirectory()) walk(p, out)
    else if (f.name.endsWith('.vue')) out.push(p)
  }
  return out
}

// 收集 <script setup> 顶层定义的名字
function collectBindings(code) {
  const ast = babelParse(code, { sourceType: 'module', plugins: ['jsx'] })
  const names = new Set()
  const add = (n) => n && names.add(n.name || n.value)
  for (const stmt of ast.program.body) {
    switch (stmt.type) {
      case 'ImportDeclaration':
        for (const s of stmt.specifiers) add(s.local)
        break
      case 'VariableDeclaration':
        for (const d of stmt.declarations) {
          // defineProps / defineEmits 的两种形态都要支持：
          //  1) const { a, b } = defineProps({...})        —— 解构绑定
          //  2) defineProps({ a: {...}, b: {...} })        —— 不接返回值，键名即 props
          const init = d.init
          if (init && init.type === 'CallExpression' && isCompilerMacro(init.callee)) {
            if (d.id.type === 'ObjectPattern') {
              collectPattern(d.id, add)
            } else if (d.id.type === 'Identifier') {
              // const props = defineProps(...)：模板里 props.xxx 是成员访问，不会误报，跳过
            }
            if (init.arguments.length && init.arguments[0].type === 'ObjectExpression') {
              for (const prop of init.arguments[0].properties) {
                if (prop.key) add(prop.key)
              }
            }
            continue
          }
          collectPattern(d.id, add)
        }
        break
      case 'FunctionDeclaration':
      case 'ClassDeclaration':
        add(stmt.id)
        break
      case 'ExportNamedDeclaration':
        if (stmt.declaration) {
          if (stmt.declaration.type === 'VariableDeclaration') {
            for (const d of stmt.declaration.declarations) collectPattern(d.id, add)
          } else add(stmt.declaration.id)
        }
        break
      case 'ExpressionStatement':
        // 裸调用形态：defineProps({...}) / defineEmits({...})
        if (stmt.expression.type === 'CallExpression' && isCompilerMacro(stmt.expression.callee)) {
          const arg = stmt.expression.arguments[0]
          if (arg && arg.type === 'ObjectExpression') {
            for (const prop of arg.properties) {
              if (prop.key) add(prop.key)
            }
          }
        }
        break
    }
  }
  return names
}

/** 是否是 Vue 编译器宏（defineProps / defineEmits / defineExpose / withDefaults） */
function isCompilerMacro(callee) {
  return callee && callee.type === 'Identifier' &&
      ['defineProps', 'defineEmits', 'defineExpose', 'withDefaults'].includes(callee.name)
}

function collectPattern(id, add) {
  if (!id) return
  switch (id.type) {
    case 'Identifier': add(id); break
    case 'ObjectPattern': for (const p of id.properties) collectPattern(p.value || p.argument, add); break
    case 'ArrayPattern': for (const el of id.elements) collectPattern(el, add); break
    case 'AssignmentPattern': collectPattern(id.left, add); break
    case 'RestElement': collectPattern(id.argument, add); break
  }
}

// 收集渲染函数里的 _ctx.xxx 引用
function collectCtxRefs(code) {
  const ast = babelParse(code, { sourceType: 'module' })
  const refs = new Set()
  const visit = (node) => {
    if (!node || typeof node !== 'object') return
    if (node.type === 'MemberExpression' && node.object.type === 'Identifier' && node.object.name === '_ctx'
        && node.property.type === 'Identifier' && !node.computed) {
      refs.add(node.property.name)
    }
    for (const k of Object.keys(node)) {
      const v = node[k]
      if (Array.isArray(v)) v.forEach(visit)
      else if (v && typeof v.type === 'string') visit(v)
    }
  }
  visit(ast.program)
  return refs
}

let issues = 0
for (const file of walk(SRC)) {
  const source = fs.readFileSync(file, 'utf8')
  const { descriptor, errors } = parse(source, { filename: file })
  if (errors.length) { console.log(`[PARSE ERROR] ${file}`); errors.forEach(e => console.log('   ', e.message)); issues++; continue }
  const scriptCode = (descriptor.scriptSetup && descriptor.scriptSetup.content) || (descriptor.script && descriptor.script.content) || ''
  const bindings = collectBindings(scriptCode)
  const tpl = compileTemplate({
    id: 'x', filename: file,
    source: descriptor.template.content,
    compilerOptions: { prefixIdentifiers: true }
  })
  if (tpl.errors.length) { console.log(`[TEMPLATE ERROR] ${file}`); tpl.errors.forEach(e => console.log('   ', e.message || e)); issues++; continue }
  const refs = collectCtxRefs(tpl.code)
  // $router / $route 等是 Vue 模板全局属性（挂在实例上），合法，不算未定义
  const undef = [...refs].filter(r => !r.startsWith('$') && !bindings.has(r))
  if (undef.length) {
    console.log(`[UNDEFINED] ${path.relative(SRC, file)} -> 模板引用了未定义变量: ${undef.join(', ')}`)
    issues++
  } else {
    console.log(`[OK] ${path.relative(SRC, file)} (${refs.size} 个模板引用全部有定义)`)
  }
}
console.log(issues === 0 ? '\n全部通过：没有「模板用了但 script 没定义」的变量。' : `\n共 ${issues} 个文件有问题`)
process.exit(issues === 0 ? 0 : 1)
