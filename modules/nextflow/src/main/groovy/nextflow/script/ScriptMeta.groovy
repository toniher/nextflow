package nextflow.script

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */

@CompileStatic
class ScriptMeta {

    static private List<String> INVALID_FUNCTION_NAMES = [
            'methodMissing',
            'propertyMissing'
    ]

    static private Map<BaseScript,ScriptMeta> REGISTRY = new HashMap<>(10)

    static ScriptMeta get(BaseScript script) {
        REGISTRY.get(script)
    }

    static ScriptMeta current() {
        get( ScriptScope.get().current() )
    }

    private Class<? extends BaseScript> clazz
    private Path scriptPath
    private ScriptIncludes includes
    private List<InvokableDef> definitions = new ArrayList<>(10)
    Path getScriptPath() { scriptPath }

    ScriptMeta(BaseScript script) {
        this.clazz = script.class
        for( def entry : definedFunctions0(script) ) {
            definitions.add(entry)
        }
    }

    @PackageScope
    void setScriptPath(Path path) {
        scriptPath = path
    }

    ScriptIncludes getScriptIncludes() {
        includes
    }

    @PackageScope void setScriptIncludes(ScriptIncludes includes) {
        this.includes = includes
    }

    List<InvokableDef> getDefinitions() {
        definitions
    }

    Set<String> getAllDefinedNames() {
        def result = new HashSet(definitions.size())
        for( def entry : definitions )
            result.add(entry.name)
        return result
    }

    @PackageScope
    static ScriptMeta register(BaseScript script) {
        def meta = new ScriptMeta(script)
        REGISTRY.put(script, meta)
        return meta
    }

    static List<FunctionDef> definedFunctions0(BaseScript script) {
        def allMethods = script.class.getDeclaredMethods()
        def result = new ArrayList(allMethods.length)
        for( Method method : allMethods ) {
            if( !Modifier.isPublic(method.getModifiers()) ) continue
            if( Modifier.isStatic(method.getModifiers())) continue
            if( method.name.startsWith('super$')) continue
            if( method.name in INVALID_FUNCTION_NAMES ) continue

            result.add(new FunctionDef(script, method))
        }
        return result
    }

    ScriptMeta addDefinition(InvokableDef invokable) {
        definitions.add(invokable)
        return this
    }

    InvokableDef getDefinition(String name) {
        definitions.find { it.name == name }
    }

    WorkflowDef getDefinedWorkflow(String name) {
        (WorkflowDef)definitions.find { it.name == name }
    }

    boolean containsDef( String name ) {
        getDefinition(name) != null
    }

    List<WorkflowDef> getDefinedWorkflows() {
        def result = new ArrayList(definitions.size())
        for( def entry : definitions ) {
            if( entry instanceof WorkflowDef )
                result.add(entry)
        }
        return result
    }

    List<ProcessDef> getDefinedProcesses() {
        def result = new ArrayList(definitions.size())
        for( def entry : definitions ) {
            if( entry instanceof ProcessDef )
                result.add(entry)
        }
        return result
    }

    List<FunctionDef> getDefinedFunctions() {
        def result = new ArrayList(definitions.size())
        for( def entry : definitions ) {
            if( entry instanceof FunctionDef )
                result.add(entry)
        }
        return result
    }

    protected <T> Set<T> getIncludedDefinitions(Class<T> type) {
        if( !includes )
            return Collections.emptySet()

        def result = new HashSet()
        for( def entry : includes.getDefinitions() ) {
            if( type.isAssignableFrom(entry.class))
                result.add(entry)
        }

        return result
    }

    Set<ProcessDef> getProcesses() {
        def result = new HashSet()
        result.addAll( getDefinedProcesses() )
        result.addAll( getIncludedDefinitions(ProcessDef) )
        return result
    }

    Set<WorkflowDef> getWorkflows() {
        def result = new HashSet()
        result.addAll( getDefinedWorkflows() )
        result.addAll( getIncludedDefinitions(WorkflowDef) )
        return result
    }

    WorkflowDef getWorkflow(String name) {
        for( def work : getDefinedWorkflows() ) {
            if( work.name == name )
                return work
        }
        for( def work : getIncludedDefinitions(WorkflowDef) ) {
            if( work.name == name )
                return work
        }
        return null
    }

    ProcessDef getProcess(String name) {
        for( def proc : getDefinedProcesses() ) {
            if( proc.name == name )
                return proc
        }
        for( def proc : getIncludedDefinitions(ProcessDef) ) {
            if( proc.name == name )
                return proc
        }
        return null
    }

    Set<FunctionDef> getFunctions() {
        def result = new HashSet()
        result.addAll( getDefinedFunctions() )
        result.addAll( getIncludedDefinitions(FunctionDef) )
        return result
    }

    Set<String> getProcessNames() {
        final processes = getProcesses()
        final result = new HashSet(processes.size())
        for( def entry : processes )
            result.add(entry.name)
        return result
    }

    InvokableDef getInvokable(String name) {
        def result = definitions.find { it.name == name }
        if( result )
            return result

        includes.getDefinitions().find { it.name == name }
    }

}
