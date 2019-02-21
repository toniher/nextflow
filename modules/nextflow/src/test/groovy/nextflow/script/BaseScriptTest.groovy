/*
 * Copyright 2013-2018, Centre for Genomic Regulation (CRG)
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

import spock.lang.Specification

import java.nio.file.Files

import groovyx.gpars.dataflow.DataflowReadChannel
import nextflow.exception.DuplicateScriptDefinitionException
import test.MockScriptRunner
import test.TestHelper
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BaseScriptTest extends Specification {

    def 'should invoke foreign functions - case 1' () {
        given:
        def folder = Files.createTempDirectory('test')
        def MODULE = folder.resolve('module.nf')
        def SCRIPT = folder.resolve('main.nf')

        MODULE.text = '''
        def alpha() {
          return 'this is alpha result'
        }   
        
        def bravo(x) { 
          return x.reverse()
        }
        
        def gamma(x,y) {
          return "$x and $y"
        }
        '''

        SCRIPT.text = """
        require "$MODULE"
   
        def local_func() {
          return "I'm local"
        }
   
        ret1 = alpha()
        ret2 = bravo('Hello')
        ret3 = gamma('Hola', 'mundo')
        ret4 = local_func()
        """

        when:
        def runner = new MockScriptRunner()
        def binding = runner.session.binding
        def result = runner.setScript(SCRIPT).execute()

        then:
        binding.ret1 == 'this is alpha result'
        binding.ret2 == 'olleH'
        binding.ret3 == 'Hola and mundo'
        binding.ret4 == "I'm local"
        binding.ret4 == result
    }

    def 'should invoke a workflow from include - case 2.a.1' () {
        given:
        def folder = Files.createTempDirectory('test')
        def MODULE = folder.resolve('module.nf')
        def SCRIPT = folder.resolve('main.nf')

        MODULE.text = '''
        process foo {
          input: val data 
          output: val result
          exec:
            result = "$data mundo"
        }     
        
        process bar {
            input: val data 
            output: val result
            exec: 
              result = data.toUpperCase()
        }   
        
        workflow alpha(data) {
            foo(data)
            bar(foo.output)
        }
        
        '''

        SCRIPT.text = """
        require "$MODULE"
   
        alpha('Hello')
        """

        when:
        def runner = new MockScriptRunner()
        def binding = runner.session.binding
        def result = runner.setScript(SCRIPT).execute()

        then:
        result.val == 'HELLO MUNDO'
        binding.hasVariable('alpha')
    }


    def 'should invoke a workflow from main - case 2.a.2' () {
        given:
        def folder = Files.createTempDirectory('test')
        def MODULE = folder.resolve('module.nf')
        def SCRIPT = folder.resolve('main.nf')

        MODULE.text = '''
        process foo {
          input: val data 
          output: val result
          exec:
            result = "$data mundo"
        }     
        
        process bar {
            input: val data 
            output: val result
            exec: 
              result = data.toUpperCase()
        }   
        
        '''

        SCRIPT.text = """
        require "$MODULE"      

        workflow alpha(data) {
            foo(data)
            bar(foo.output)
        }
   
        alpha('Hello')
        """

        when:
        def runner = new MockScriptRunner()
        def binding = runner.session.binding
        def result = runner.setScript(SCRIPT).execute()

        then:
        result.val == 'HELLO MUNDO'
        binding.hasVariable('alpha')
    }

    def 'should invoke an anonymous workflow' () {
        given:
        def folder = Files.createTempDirectory('test')
        def MODULE = folder.resolve('module.nf')
        def SCRIPT = folder.resolve('main.nf')

        MODULE.text = '''
        process foo {
          input: val data 
          output: val result
          exec:
            result = "$data mundo"
        }     
        
        process bar {
            input: val data 
            output: val result
            exec: 
              result = data.toUpperCase()
        }   
        
        '''

        SCRIPT.text = """
        require "$MODULE"
   
        data = 'Hello'
        workflow {
            foo(data)
            bar(foo.output)
        }
        """

        when:
        def runner = new MockScriptRunner()
        def result = runner.setScript(SCRIPT).execute()

        then:
        result.val == 'HELLO MUNDO'
    }

    def 'should define a process and invoke it' () {
        given:
        def folder = Files.createTempDirectory('test')
        def MODULE = folder.resolve('module.nf')
        def SCRIPT = folder.resolve('main.nf')

        MODULE.text = '''
        process foo {
          input: val sample
          output: stdout() 
          script:
          /echo Hello $sample/
        }        
        '''

        SCRIPT.text = """
        require "$MODULE"
        hello_ch = Channel.from('world')
        
        workflow {
            foo(hello_ch)
        }
        """

        when:
        def runner = new MockScriptRunner()
        def result = runner.setScript(SCRIPT).execute()
        then:
        noExceptionThrown()
        result instanceof DataflowReadChannel
        result.val == 'echo Hello world'

        cleanup:
        folder?.deleteDir()
    }

    def 'should define a process with multiple inputs' () {
        given:
        def folder = TestHelper.createInMemTempDir()
        def MODULE = folder.resolve('module.nf')
        def SCRIPT = folder.resolve('main.nf')

        MODULE.text = '''
        process foo {
          input: 
            val sample
            set pairId, reads
          output: 
            stdout() 
          script:
            /echo sample=$sample pairId=$pairId reads=$reads/
        }
        '''

        SCRIPT.text = """
        require 'module.nf'

        workflow {
            ch1 = Channel.from('world')
            ch2 = Channel.value(['x', '/some/file'])
            foo(ch1, ch2)
        }
        """

        when:
        def runner = new MockScriptRunner()
        def result = runner.setScript(SCRIPT).execute()
        then:
        noExceptionThrown()
        result instanceof DataflowReadChannel
        result.val == 'echo sample=world pairId=x reads=/some/file'
    }

    

    def 'should compose processes' () {

        given:
        def folder = TestHelper.createInMemTempDir()
        def MODULE = folder.resolve('module.nf')
        def SCRIPT = folder.resolve('main.nf')

        MODULE.text = '''
        process foo {
          input: 
            val alpha
          output: 
            val delta
            val gamma
          script:
            delta = alpha
            gamma = 'world\'
            /nope/
        }
        
        process bar {
           input:
             val xx
             val yy 
           output:
             stdout()
           script:
            /echo $xx $yy/            
        }
        '''

        when:
        SCRIPT.text = """
        require 'module.nf'        

        workflow {
            bar(foo('Ciao'))
        }
        """
        def runner = new MockScriptRunner()
        def result = runner.setScript(SCRIPT).execute()
        then:
        noExceptionThrown()
        result instanceof DataflowReadChannel
        result.val == 'echo Ciao world'


        when:
        SCRIPT.text = """
        require 'module.nf'        
        
        workflow {
          (ch0, ch1) = foo('Ciao')
        }
        """
        runner = new MockScriptRunner()
        result = runner.setScript(SCRIPT).execute()
        then:
        noExceptionThrown()
        result[0].val == 'Ciao'
        result[1].val == 'world'
    }


    def 'should inject params in module' () {
        given:
        def folder = TestHelper.createInMemTempDir()
        def MODULE = folder.resolve('module.nf')
        def SCRIPT = folder.resolve('main.nf')

        MODULE.text = '''
        params.foo = 'x' 
        params.bar = 'y'
        
        process foo {
          output: stdout() 
          script:
          /echo $params.foo $params.bar/
        }
        '''

        // inject params in the module
        // and invoke the process 'foo'
        SCRIPT.text = """ 
        require "module.nf", params:[foo:'Hello', bar: 'world']
            
        workflow { 
            foo() 
        }
        """

        when:
        def runner = new MockScriptRunner()
        def result = runner.setScript(SCRIPT).execute()
        then:
        noExceptionThrown()
        result instanceof DataflowReadChannel
        result.val == 'echo Hello world'
        
    }

    def 'should error on process duplication' () {

        given:
        def folder = TestHelper.createInMemTempDir()
        def LIB1 = folder.resolve('lib1.nf')
        def LIB2 = folder.resolve('lib2.nf')
        def SCRIPT = folder.resolve('main.nf')

        def lib = '''
        process foo {
          'echo ciao'
        }    
        '''
        LIB1.text = lib
        LIB2.text = lib

        SCRIPT.text = """ 
        require 'lib1.nf'
        require 'lib2.nf'

        workflow {
            foo()
        }
        """

        when:
        new MockScriptRunner()
                .setScript(SCRIPT)
                .execute()
        then:
        def err = thrown(DuplicateScriptDefinitionException)
        err.message == """\
            Duplicate definition `foo` in the following scripts:
            - ${LIB2.toUriString()}
            - ${LIB1.toUriString()}
            """.stripIndent()

    }

    def 'should invoke custom functions' () {
        given:
        def folder = TestHelper.createInMemTempDir()
        def MODULE = folder.resolve('module.nf')
        def SCRIPT = folder.resolve('main.nf')

        MODULE.text = '''
        def foo(str) {
          str.reverse()
        }
        
        def bar(a, b) {
          return "$a $b!"
        }
        '''

        SCRIPT.text = """
        require 'module.nf'

        def str = foo('dlrow')
        return bar('Hello', str)
        """

        when:
        def runner = new TestScriptRunner()
        def result = runner.setScript(SCRIPT).execute()
        then:
        noExceptionThrown()
        result == 'Hello world!'
    }

    def 'should access module variables' () {
        given:
        def folder = TestHelper.createInMemTempDir()
        def MODULE = folder.resolve('module.nf')
        def SCRIPT = folder.resolve('main.nf')

        MODULE.text = '''     
        params.x = 'Hello world'
        FOO = params.x   
        process foo {
          output: stdout() 
          script:
          "echo $FOO"
        }
        '''

        SCRIPT.text = """
        require 'module.nf', params:[x: 'Hola mundo']
        workflow {
            return foo()
        }    
        """

        when:
        def runner = new MockScriptRunner()
        def result = runner.setScript(SCRIPT).execute()
        then:
        noExceptionThrown()
        result.val == 'echo Hola mundo'
    }

    def 'should pipe processes' () {
        given:
        def folder = Files.createTempDirectory('test')
        def MODULE = folder.resolve('module.nf')
        def SCRIPT = folder.resolve('main.nf')

        MODULE.text = '''
        process foo {
          input: val data 
          output: val result
          exec:
            result = "$data mundo"
        }     
        
        process bar {
            input: val data 
            output: val result
            exec: 
              result = data.toUpperCase()
        }   
        
        '''

        SCRIPT.text = """
        require "$MODULE"      

        workflow {
           Channel.from('Hello') | foo | bar
        }
        """

        when:
        def runner = new MockScriptRunner()
        def result = runner.setScript(SCRIPT).execute()

        then:
        result.val == 'HELLO MUNDO'
    }

}
