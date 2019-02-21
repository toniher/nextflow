package nextflow.script

import spock.lang.Specification

import groovy.transform.InheritConstructors

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ScriptMetaTest extends Specification {

    @InheritConstructors
    static class FooScript extends BaseScript {
        @Override
        protected Object runScript() { null }
    }

    def 'should return all defined names' () {

        given:
        def script = new FooScript(new ScriptBinding())

        def proc1 = new ProcessDef(script, 'proc1', Mock(ProcessConfig), Mock(TaskBody))
        def proc2 = new ProcessDef(script, 'proc2', Mock(ProcessConfig), Mock(TaskBody))
        def func1 = new FunctionDef(name: 'func1')
        def work1 = new WorkflowDef(Mock(TaskBody), 'work1')


        def inc_p1 = new ProcessDef(script, 'inc_p1', Mock(ProcessConfig), Mock(TaskBody))
        def inc_f1 = new FunctionDef(name: 'inc_func1')
        def inc_f2 = new FunctionDef(name: 'inc_func2')
        def inc_w1 = new WorkflowDef(Mock(TaskBody), 'inc_w1')
        def inc_w2 = new WorkflowDef(Mock(TaskBody), 'inc_w2')

        def includes = Mock(ScriptIncludes)
        includes.getDefinitions() >> [inc_p1, inc_f1, inc_f2, inc_w1, inc_w2 ]

        def meta = new ScriptMeta(script)
        meta.setScriptIncludes(includes)

        when:
        meta.addDefinition(func1)
        meta.addDefinition(work1)
        meta.addDefinition(proc1)
        meta.addDefinition(proc2)

        then:
        meta.getDefinition('work1') == work1
        meta.getDefinition('func1') == func1
        meta.getDefinition('proc1') == proc1
        meta.getDefinition('proc2') == proc2
        meta.getDefinition('inc_p1') == null
        meta.getDefinition('inc_w2') == null

        then:
        meta.getInvokable('work1') == work1
        meta.getInvokable('proc1') == proc1
        meta.getInvokable('inc_p1') == inc_p1
        meta.getInvokable('inc_w2') == inc_w2

        then:
        meta.getAllDefinedNames() == ['proc1','proc2','func1','work1'] as Set

        then:
        meta.containsDef('proc1')
        meta.containsDef('proc2')
        meta.containsDef('func1')
        meta.containsDef('work1')
        !meta.containsDef('proc3')

        then:
        meta.getDefinedProcesses() == [proc1, proc2]
        meta.getDefinedWorkflows() == [work1]
        meta.getDefinedFunctions() == [func1]

        then:
        meta.getFunctions() == [func1, inc_f1, inc_f2] as Set
        meta.getWorkflows() == [work1, inc_w1, inc_w2] as Set
        meta.getProcesses() == [proc1, proc2, inc_p1] as Set
        meta.getProcessNames() == ['proc1','proc2', 'inc_p1'] as Set

        then:
        meta.getProcess('proc1') == proc1
        meta.getProcess('inc_p1') == inc_p1
        meta.getProcess('foo') == null
        meta.getWorkflow('work1') == work1
        meta.getWorkflow('inc_w1') == inc_w1
        meta.getWorkflow('func1') == null

    }
}
