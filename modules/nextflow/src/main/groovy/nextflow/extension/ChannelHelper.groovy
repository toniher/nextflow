package nextflow.extension

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovyx.gpars.dataflow.DataflowBroadcast
import groovyx.gpars.dataflow.DataflowQueue
import groovyx.gpars.dataflow.DataflowReadChannel
import groovyx.gpars.dataflow.DataflowVariable
import groovyx.gpars.dataflow.DataflowWriteChannel
import groovyx.gpars.dataflow.expression.DataflowExpression
import groovyx.gpars.dataflow.stream.DataflowStreamReadAdapter
import nextflow.Channel
import nextflow.script.WorkflowScope
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ChannelHelper {

    static Map<DataflowQueue, DataflowBroadcast> bridges = new HashMap<>(10)

    static DataflowWriteChannel createBy(DataflowReadChannel channel) {
        create( channel instanceof DataflowExpression )
    }

    static DataflowWriteChannel create(boolean value=false) {
        if( value )
            return new DataflowVariable()

        if( WorkflowScope.get().current() == null )
            return new DataflowQueue()

        else
            return new DataflowBroadcast()
    }

    static DataflowReadChannel get(channel) {
        if (channel instanceof DataflowExpression)
            return channel

        if (channel instanceof DataflowQueue)
            return get(channel)

        if (channel instanceof DataflowBroadcast)
            return get(channel)

        throw new IllegalArgumentException("Illegal channel source type: ${channel?.getClass()?.getName()}")
    }

    static DataflowReadChannel get(DataflowQueue queue) {
        def workflow = WorkflowScope.get().current()
        if( workflow==null )
            return queue

        def broadcast = bridges.get(queue)
        if( broadcast == null ) {
            broadcast = new DataflowBroadcast()
            bridges.put(queue, broadcast)
        }
        return broadcast.createReadChannel()
    }

    static DataflowReadChannel get(DataflowBroadcast channel) {
        def workflow = WorkflowScope.get().current()
        if( workflow==null )
            throw new IllegalStateException("Broadcast channel are only allowed in a workflow definition scope")

        channel.createReadChannel()
    }

    static void broadcast() {
        // connect all dataflow queue variables to associated broadcast channel 
        for( DataflowQueue queue : bridges.keySet() ) {
            def broadcast = bridges.get(queue)
            queue.into(broadcast)
        }
    }

    static boolean isChannelQueue(obj) {
        obj instanceof DataflowQueue || obj instanceof DataflowStreamReadAdapter
    }


    @PackageScope
    static DataflowWriteChannel close0(DataflowWriteChannel source) {
        if( source instanceof DataflowExpression ) {
            if( !source.isBound() )
                source.bind(Channel.STOP)
        }
        else {
            source.bind(Channel.STOP)
        }
        return source
    }

}
