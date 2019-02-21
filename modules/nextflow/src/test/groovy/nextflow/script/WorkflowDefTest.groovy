package nextflow.script

import spock.lang.Specification

import nextflow.ast.NextflowDSL
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class WorkflowDefTest extends Specification {

    static abstract class TestScript extends BaseScript {

        Object run() {
            runScript()
            return this
        }

    }

    def 'should parse workflow' () {

        given:
        def config = new CompilerConfiguration()
        config.setScriptBaseClass(TestScript.class.name)
        config.addCompilationCustomizers( new ASTTransformationCustomizer(NextflowDSL))

        def SCRIPT = '''
                    
            workflow alpha {
              print 'Hello world'
            }
        
            workflow bravo(foo, bar) {
              print foo
              print bar
              return foo+bar
            }
            
            workflow delta(foo) {
                println foo+bar
            }

            workflow empty { }
        '''

        when:
        def script = (TestScript)new GroovyShell(config).parse(SCRIPT).run()
        def meta = ScriptMeta.get(script)
        then:
        meta.definedWorkflows.size() == 4
        meta.getDefinedWorkflow('alpha') .declaredInputs == []
        meta.getDefinedWorkflow('alpha') .declaredVariables == []
        meta.getDefinedWorkflow('alpha') .source.stripIndent() == "print 'Hello world'\n"

        meta.getDefinedWorkflow('bravo') .declaredInputs == ['foo', 'bar']
        meta.getDefinedWorkflow('bravo') .declaredVariables == []
        meta.getDefinedWorkflow('bravo') .source.stripIndent() == "print foo\nprint bar\nreturn foo+bar\n"

        meta.getDefinedWorkflow('delta') .declaredInputs == ['foo']
        meta.getDefinedWorkflow('delta') .declaredVariables == ['bar']

        meta.getDefinedWorkflow('empty') .source == ''
        meta.getDefinedWorkflow('empty') .declaredInputs == []
        meta.getDefinedWorkflow('empty') .declaredVariables == []
    }

    def 'should define anonymous workflow' () {
        given:
        def config = new CompilerConfiguration()
        config.setScriptBaseClass(TestScript.class.name)
        config.addCompilationCustomizers( new ASTTransformationCustomizer(NextflowDSL))

        def SCRIPT = '''
                    
            workflow {
              print 1
              print 2
            }
        '''

        when:
        def binding = new ScriptBinding()
        def script = (TestScript)new GroovyShell(binding, config).parse(SCRIPT).run()
        def meta = ScriptMeta.get(script)
        then:
        meta.getDefinedWorkflow(null).getSource().stripIndent() == 'print 1\nprint 2\n'

    }

    def 'should run workflow block' () {

        given:
        def config = new CompilerConfiguration()
        config.setScriptBaseClass(TestScript.class.name)
        config.addCompilationCustomizers( new ASTTransformationCustomizer(NextflowDSL))

        def SCRIPT = '''
                    
            workflow alpha(x) {
              return "$x world"
            }
       
        '''

        when:
        def script = (TestScript)new GroovyShell(config).parse(SCRIPT).run()
        def workflow = ScriptMeta.get(script).getDefinedWorkflow('alpha')
        then:
        workflow.declaredInputs == ['x']

        when:
        def binding = new ScriptBinding()
        def result = workflow.invoke('Hello', binding)
        then:
        result == 'Hello world'
        binding.alpha.output == result

    }

}
