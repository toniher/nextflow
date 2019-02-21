package nextflow.script

import groovy.transform.CompileStatic

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ScriptScope {

    static ScriptScope INSTANCE = new ScriptScope()

    static ScriptScope get() { INSTANCE }

    private List<BaseScript> stack = new ArrayList<>()

    BaseScript current() {
        stack ? stack.get(0) : null
    }

    void push(BaseScript script) {
        stack.push(script)
    }

    BaseScript pop() {
        stack.pop()
    }

    int size() {
        stack.size()
    }

    boolean asBoolean() {
        stack.size()>0
    }
}
