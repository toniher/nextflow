/*
 * Copyright 2013-2019, Centre for Genomic Regulation (CRG)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.script

import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.exception.IllegalInvocationException
import nextflow.extension.ChannelHelper
import nextflow.processor.TaskProcessor
/**
 * Any user defined script will extends this class, it provides the base execution context
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
abstract class BaseScript extends Script {

    private static Object[] EMPTY_ARGS = [] as Object[]

    private Session session

    private ProcessFactory processFactory

    private TaskProcessor taskProcessor

    private boolean module

    private ScriptMeta meta

    @Lazy InputStream stdin = { System.in }()

    BaseScript() {
        meta = ScriptMeta.register(this)
    }

    BaseScript(Binding binding) {
        super(binding)
        meta = ScriptMeta.register(this)
    }

    @Override
    ScriptBinding getBinding() {
        (ScriptBinding)super.getBinding()
    }

    /**
     * Holds the configuration object which will used to execution the user tasks
     */
    protected Map getConfig() {
        log.warn "The access of `config` object is deprecated"
        session.getConfig()
    }

    /**
     * Access to the last *process* object -- only for testing purpose
     */
    @PackageScope
    TaskProcessor getTaskProcessor() { taskProcessor }

    /**
     * Enable disable task 'echo' configuration property
     * @param value
     */
    protected void echo(boolean value = true) {
        log.warn "The use of `echo` method is deprecated"
        session.getConfig().process.echo = value
    }

    private void setup() {
        module = binding.module
        session = binding.getSession()
        processFactory = session.newProcessFactory(this)
        meta.scriptIncludes = new ScriptIncludes(this)

        binding.setVariable( 'baseDir', session.baseDir )
        binding.setVariable( 'workDir', session.workDir )
        binding.setVariable( 'workflow', session.workflowMetadata )
        binding.setVariable( 'nextflow', session.workflowMetadata?.nextflow )
    }

    protected process( Map<String,?> args, String name, Closure body ) {
        throw new DeprecationException("This process invocation syntax is deprecated")
    }

    protected process( String name, Closure body ) {

        if( module ) {
            def proc = processFactory.defineProcess(name, body)
            meta.addDefinition(proc)
        }
        else {
            // legacy process definition an execution
            taskProcessor = processFactory.createProcessor(name, body)
            taskProcessor.run()
        }
    }

    protected workflow(TaskBody body) {
        if( module )
            throw new IllegalArgumentException("Anonymous workflow declaration is not allowed in module script")
        def workflow = new WorkflowDef(body)
        meta.addDefinition(workflow)
        def result = workflow.invoke(EMPTY_ARGS, binding)
        // finally bridge dataflow queues
        ChannelHelper.broadcast()
        return result
    }

    protected workflow(TaskBody body, String name, List<String> declaredInputs) {
        meta.addDefinition(new WorkflowDef(body,name,declaredInputs))
    }

    protected void require(path) {
        require(Collections.emptyMap(), path)
    }

    protected void require(Map opts, path) {
        final params = opts.params ? (Map)opts.params : null
        meta.scriptIncludes.load(path, params)
    }

    @Override
    Object invokeMethod(String name, Object args) {
        def invokable = meta.getInvokable(name)
        if( !invokable )
            return super.invokeMethod(name,args)

        // case 1 - invoke foreign function definition
        if( invokable instanceof FunctionDef )
            return invokable.invoke(args)

        final current = WorkflowScope.get().current()
        if( invokable instanceof WorkflowDef ) {
            // case 2.b - workflow nested invocation
            if( current )
                return invokable.invoke(args, current.context)

            // case 2.a - workflow invocation from main
            if( !module )
                return invokable.invoke(args, binding)

            else
                invokeError(invokable)
        }

        // case 3 - process invocation from within a workflow
        if( invokable instanceof ProcessDef ) {
            if( current )
                return invokable.invoke(args, current.context)
            else
                invokeError(invokable)
        }

        throw new IllegalArgumentException("Unknown invocation: name=$invokable.name type=${invokable.class.name}")
    }

    private void invokeError(InvokableDef invokable) {
        def message
        if( invokable instanceof WorkflowDef )
            message = "Workflow $invokable.name cannot be invoked from a module script"
        else if( invokable instanceof ProcessDef )
            message = "Process $invokable.name can only be invoked from a workflow context"
        else
            message = "Invalid invocation context: $invokable.name"
        throw new IllegalInvocationException(message)
    }

    Object run() {
        setup()
        ScriptScope.get().push(this)
        try {
            runScript()
        }
        finally {
            ScriptScope.get().pop()
        }
    }

    protected abstract Object runScript()

}
