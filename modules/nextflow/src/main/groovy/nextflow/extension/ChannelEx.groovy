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

package nextflow.extension

import groovyx.gpars.agent.Agent
import groovyx.gpars.dataflow.DataflowBroadcast
import groovyx.gpars.dataflow.DataflowQueue
import groovyx.gpars.dataflow.DataflowReadChannel
import groovyx.gpars.dataflow.DataflowWriteChannel
import nextflow.Channel
import nextflow.Global
import nextflow.dag.NodeMarker
import nextflow.script.ProcessDef
import nextflow.script.WorkflowDef
import nextflow.script.WorkflowScope
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ChannelEx {

    static boolean isChannelQueue(DataflowReadChannel ch) {
        ChannelHelper.isChannelQueue(ch)
    }

    /**
     * Assign the {@code source} channel to a global variable with the name specified by the closure.
     * For example:
     * <pre>
     *     Channel.from( ... )
     *            .map { ... }
     *            .set { newChannelName }
     * </pre>
     *
     * @param DataflowReadChannel
     * @param holder A closure that must define a single variable expression
     */
    static void set(DataflowWriteChannel source, Closure holder ) {
        final name = CaptureProperties.capture(holder)
        if( !name )
            throw new IllegalArgumentException("Missing name to which set the channel variable")

        if( name.size()>1 )
            throw new IllegalArgumentException("Operation `set` does not allow more than one target name")

        final binding = Global.session.binding
        binding.setVariable(name[0], source)
    }

    static DataflowWriteChannel dump(final DataflowWriteChannel source, Closure closure = null) {
        dump(source, Collections.emptyMap(), closure)
    }

    static DataflowWriteChannel dump(final DataflowWriteChannel source, Map opts, Closure closure = null) {
        def op = new DumpOp(opts, closure)
        if( op.isEnabled() ) {
            op.setSource(source)
            def target = op.apply()
            NodeMarker.addOperatorNode('dump', source, target)
            return target
        }
        else {
            return source
        }
    }

    /**
     * Creates a channel emitting the entries in the collection to which is applied
     * @param values
     * @return
     */
    static DataflowQueue channel(Collection values) {
        def target = new DataflowQueue()
        def itr = values.iterator()
        while( itr.hasNext() ) target.bind(itr.next())
        target.bind(Channel.STOP)
        NodeMarker.addSourceNode('channel',target)
        return target
    }

    /**
     * Close a dataflow queue channel binding a {@link Channel#STOP} item
     *
     * @param source The source dataflow channel to be closed.
     */
    @Deprecated
    static DataflowWriteChannel close(DataflowWriteChannel source) {
        return ChannelHelper.close0(source)
    }

    /**
     * INTERNAL ONLY API
     * <p>
     * Add the {@code update} method to an {@code Agent} so that it call implicitly
     * the {@code Agent#updateValue} method
     *
     */
    static void update(Agent self, Closure message ) {
        assert message != null

        self.send {
            message.call(it)
            updateValue(it)
        }

    }

    static private DataflowReadChannel read0(DataflowWriteChannel channel) {
        if( channel instanceof DataflowReadChannel )
            return channel
        if( channel instanceof DataflowBroadcast )
            return channel.createReadChannel()
        throw new IllegalArgumentException("Illegal pipe input channel: ${channel?.class?.name}")
    }

    static private WorkflowDef checkScope() {
        def scope = WorkflowScope.get().current()
        if( scope == null ) throw new IllegalArgumentException("Process invocation are only allowed within a workflow context")
        return scope
    }

    static Object or( DataflowWriteChannel left, Object right ) {
        def scope = checkScope()
        if( right instanceof ProcessDef ) {
            return right.invoke(read0(left), scope.context)
        }
        if( right instanceof WorkflowDef ) {
            return right.invoke(read0(left), scope.context)
        }
        throw new IllegalArgumentException("Pipe right operand must be a process or a workflow definition")
    }


}
