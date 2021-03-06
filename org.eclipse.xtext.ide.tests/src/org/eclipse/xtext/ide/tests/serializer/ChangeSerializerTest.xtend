/*******************************************************************************
 * Copyright (c) 2017 TypeFox GmbH (http://www.typefox.io) and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.xtext.ide.tests.serializer

import com.google.inject.Inject
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.ide.tests.testlanguage.partialSerializationTestLanguage.MandatoryValue
import org.eclipse.xtext.ide.tests.testlanguage.partialSerializationTestLanguage.Node
import org.eclipse.xtext.ide.tests.testlanguage.partialSerializationTestLanguage.PartialSerializationTestLanguageFactory
import org.eclipse.xtext.ide.tests.testlanguage.tests.PartialSerializationTestLanguageInjectorProvider
import org.eclipse.xtext.testing.InjectWith
import org.eclipse.xtext.testing.XtextRunner
import org.eclipse.xtext.testing.util.InMemoryURIHandler
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.eclipse.xtext.ide.tests.testlanguage.partialSerializationTestLanguage.TwoChildLists
import org.eclipse.xtext.ide.tests.testlanguage.partialSerializationTestLanguage.ChildWithSubChilds
import org.eclipse.xtext.ide.tests.testlanguage.partialSerializationTestLanguage.TwoChilds

/**
 * @author Moritz Eysholdt - Initial contribution and API
 */
@RunWith(XtextRunner)
@InjectWith(PartialSerializationTestLanguageInjectorProvider)
class ChangeSerializerTest {

	extension PartialSerializationTestLanguageFactory fac = PartialSerializationTestLanguageFactory.eINSTANCE

	@Inject extension ChangeSerializerTestHelper
	
	@Test
	def void testNoop() {
		val serializer = newChangeSerializer()
		serializer.endRecordChangesToTextDocuments === ""
	}

	@Test
	def void testSimple() {
		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file1.pstl" -> '''#2 foo'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file1.pstl", MandatoryValue)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.name = "bar"
		]
		serializer.endRecordChangesToTextDocuments === '''
			----------------- inmemory:/file1.pstl (syntax: <offset|text>) -----------------
			#2 <3:3|bar>
			--------------------------------------------------------------------------------
			3 3 "foo" -> "bar"
		'''
	}

	@Test
	def void testTwoChildren() {
		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file1.pstl" -> '''#1 root { foo1; foo2; }'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file1.pstl", Node)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.children.get(0).name = "bazz4"
			model.children.get(1).name = "bazz5"
		]
		serializer.endRecordChangesToTextDocuments === '''
			----------------- inmemory:/file1.pstl (syntax: <offset|text>) -----------------
			#1 root { <10:4|bazz4>; <16:4|bazz5>; }
			--------------------------------------------------------------------------------
			10 4 "foo1" -> "bazz4"
			16 4 "foo2" -> "bazz5"
		'''
	}

	@Test
	def void testInsertOneChild() {
		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file1.pstl" -> '''#1 root { child1 { foo1; } }'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file1.pstl", Node)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.children.get(0).children += createNode => [name = "bazz"]
		]
		serializer.endRecordChangesToTextDocuments === '''
			----------------- inmemory:/file1.pstl (syntax: <offset|text>) -----------------
			#1 root { child1 { foo1; <25:0|bazz; >} }
			--------------------------------------------------------------------------------
			25 0 "" -> "bazz; "
		'''
	}
	
	@Test
	def void testInsertBeforeComment() {
		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file1.pstl" -> '''
			#1 root {
				/**/ 
				child1;
			}
		'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file1.pstl", Node)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.children.add(0, createNode => [name = "bazz"])
		]
		serializer.endRecordChangesToTextDocuments === '''
			----------------- inmemory:/file1.pstl (syntax: <offset|text>) -----------------
			#1 root {<9:0| bazz;>
				/**/ 
				child1;
			}
			--------------------------------------------------------------------------------
			9 0 "" -> " bazz;"
		'''
	}

	@Test
	def void testInsertTwoChild() {
		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file1.pstl" -> '''#1 root { child1 { foo1; } }'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file1.pstl", Node)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.children.get(0).children += createNode => [name = "bazz1"]
			model.children.get(0).children += createNode => [name = "bazz2"]
		]
		serializer.endRecordChangesToTextDocuments === '''
			----------------- inmemory:/file1.pstl (syntax: <offset|text>) -----------------
			#1 root { child1 { foo1; <25:0|bazz1; bazz2; >} }
			--------------------------------------------------------------------------------
			25 0 "" -> "bazz1; bazz2; "
		'''
	}

	@Test
	def void testDeleteChild() {
		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file1.pstl" -> '''#1 root { child1 { foo1; } }'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file1.pstl", Node)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			EcoreUtil.remove(model.children.get(0).children.get(0))
		]
		serializer.endRecordChangesToTextDocuments === '''
			----------------- inmemory:/file1.pstl (syntax: <offset|text>) -----------------
			#1 root { child1 { <19:5|> } }
			--------------------------------------------------------------------------------
			19 5 "foo1;" -> ""
		'''
	}
	
	@Test
	def void testDeleteTwoChildren() {
		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file1.pstl" -> '''#1 root { child1; child2; }'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file1.pstl", Node)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			EcoreUtil.remove(model.children.get(1))
			EcoreUtil.remove(model.children.get(0))
		]
		serializer.endRecordChangesToTextDocuments === '''
			----------------- inmemory:/file1.pstl (syntax: <offset|text>) -----------------
			#1 root { <10:7|> <18:7|> }
			--------------------------------------------------------------------------------
			10 7 "child1;" -> ""
			18 7 "child2;" -> ""
		'''
	}

	@Test
	def void testRenameLocal() {
		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file1.pstl" -> '''#1 root { foo1; foo2 { ref foo1 } }'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file1.pstl", Node)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.children.get(0).name = "bazz4"
		]
		serializer.endRecordChangesToTextDocuments === '''
			----------------- inmemory:/file1.pstl (syntax: <offset|text>) -----------------
			#1 root { <10:4|bazz4>; foo2 { ref <27:4|bazz4> } }
			--------------------------------------------------------------------------------
			10 4 "foo1" -> "bazz4"
			27 4 "foo1" -> "bazz4"
		'''
	}

	@Test
	def void testRenameGlobal1() {
		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file1.pstl" -> '''#1 root1;'''
		fs += "inmemory:/file2.pstl" -> '''#1 root2 { ref root1 }'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file1.pstl", Node)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.name = "newroot"
		]
		Assert.assertEquals(1, model.eResource.resourceSet.resources.size)
		serializer.endRecordChangesToTextDocuments === '''
			----------------- inmemory:/file1.pstl (syntax: <offset|text>) -----------------
			#1 <3:5|newroot>;
			--------------------------------------------------------------------------------
			3 5 "root1" -> "newroot"
			----------------- inmemory:/file2.pstl (syntax: <offset|text>) -----------------
			#1 root2 { ref <15:5|newroot> }
			--------------------------------------------------------------------------------
			15 5 "root1" -> "newroot"
		'''
	}
	
	@Test
	def void testRenameFqn1() {
		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file1.pstl" -> '''
			#1 r {
				X refs a1.a2 X.a1.a2 r.X.a1.a2 { a1 { a2 refs a2 { a3 { ref a3 } } } }
				Y refs b1.b2 Y.b1.b2 r.Y.b1.b2 { b1 { b2 { ref b2 } } }
			}
		'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file1.pstl", Node)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.children.head.children.head.children.head.name = "b"
		]
		Assert.assertEquals(1, model.eResource.resourceSet.resources.size)
		serializer.endRecordChangesToTextDocuments === '''
			----------------- inmemory:/file1.pstl (syntax: <offset|text>) -----------------
			#1 r {
				X refs <15:5|a1.b> <21:7|a1.b> <29:9|a1.b> { a1 { <46:2|b> refs <54:2|b> { a3 { ref a3 } } } }
				Y refs b1.b2 Y.b1.b2 r.Y.b1.b2 { b1 { b2 { ref b2 } } }
			}
			--------------------------------------------------------------------------------
			15 5 "a1.a2" -> "a1.b"
			21 7 "X.a1.a2" -> "a1.b"
			29 9 "r.X.a1.a2" -> "a1.b"
			46 2 "a2" -> "b"
			54 2 "a2" -> "b"
		'''
	}
	
	@Test
	def void testRenameFqn1ValueConversion() {
		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file1.pstl" -> '''
			#1 r {
				X refs ^a1.^a2 ^X.^a1.^a2 ^r.^X.^a1.^a2 { a1 { a2 refs ^a2 { a3 { ref ^a3 } } } }
				Y refs ^b1.^b2 ^Y.^b1.^b2 ^r.^Y.^b1.^b2 { b1 { b2 { ref b2 } } }
			}
		'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file1.pstl", Node)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.children.head.children.head.children.head.name = "b"
		]
		Assert.assertEquals(1, model.eResource.resourceSet.resources.size)
		serializer.endRecordChangesToTextDocuments === '''
			----------------- inmemory:/file1.pstl (syntax: <offset|text>) -----------------
			#1 r {
				X refs <15:7|a1.b> <23:10|a1.b> <34:13|a1.b> { a1 { <55:2|b> refs <63:3|b> { a3 { ref ^a3 } } } }
				Y refs ^b1.^b2 ^Y.^b1.^b2 ^r.^Y.^b1.^b2 { b1 { b2 { ref b2 } } }
			}
			--------------------------------------------------------------------------------
			15  7 "^a1.^a2" -> "a1.b"
			23 10 "^X.^a1.^a2" -> "a1.b"
			34 13 "^r.^X.^a1.^a2" -> "a1.b"
			55  2 "a2" -> "b"
			63  3 "^a2" -> "b"
		'''
	}
	
	@Test
	def void testResourceURIChange() {
		val fs = new InMemoryURIHandler()
		fs += "inmemory:/f.pstl" -> '''#1 root { }'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/f.pstl", Node)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.eResource.URI = org.eclipse.emf.common.util.URI.createURI("inmemory:/x.pstl")
		]
		serializer.endRecordChangesToTextDocuments === '''
			----- renamed inmemory:/f.pstl to inmemory:/x.pstl (syntax: <offset|text>) -----
			(no changes)
			--------------------------------------------------------------------------------
		'''
	}
	
	@Test
	def void testAddChildElement() {

		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file-move.pstl" -> '''
		#22 {
			child1
			children1 {
				child2 child3
			}
		}'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file-move.pstl", TwoChildLists)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.directChildren.add(createMandatoryValue => [name = "newChild"])
		]
		serializer.endRecordChangesToTextDocuments === '''
			--------------- inmemory:/file-move.pstl (syntax: <offset|text>) ---------------
			#22 {
				child1<13:0| newChild>
				children1 {
					child2 child3
				}
			}
			--------------------------------------------------------------------------------
			13 0 "" -> " newChild"
			'''
	}
	
	@Test
	def void testMoveElement() {

		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file-move1.pstl" -> '''
		#22 {
			child1
			children1 {
				child2 jumper
			}
		}'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file-move1.pstl", TwoChildLists)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.directChildren.add(model.childsList.children.findFirst[name == "jumper"])
		]
		serializer.endRecordChangesToTextDocuments === '''
		-------------- inmemory:/file-move1.pstl (syntax: <offset|text>) ---------------
		#22 {
			child1<13:0| jumper>
			children1 {
				child2 <36:6|>
			}
		}
		--------------------------------------------------------------------------------
		13 0 "" -> " jumper"
		36 6 "jumper" -> ""
		'''
	}
	
	@Test
	def void testMoveElement_2() {

		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file-move.pstl" -> '''
		#22 {
			child1
			children1 {
				child2 jumper
			}
		}'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file-move.pstl", TwoChildLists)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.directChildren.add(0, model.childsList.children.findFirst[name == "jumper"])
		]
		serializer.endRecordChangesToTextDocuments === '''
			--------------- inmemory:/file-move.pstl (syntax: <offset|text>) ---------------
			#22 {<5:0| jumper>
				child1
				children1 {
					child2 <36:6|>
				}
			}
			--------------------------------------------------------------------------------
			 5 0 "" -> " jumper"
			36 6 "jumper" -> ""
		'''
	}
	
	@Test
	def void testMoveElement_2a() {

		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file-move2a.pstl" -> '''
		#22 {
			child1 child3
			children1 {
				child2 jumper
			}
		}'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file-move2a.pstl", TwoChildLists)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.directChildren.add(1, model.childsList.children.findFirst[name == "jumper"])
		]
		serializer.endRecordChangesToTextDocuments === '''
			-------------- inmemory:/file-move2a.pstl (syntax: <offset|text>) --------------
			#22 {
				child1 <14:0|jumper >child3
				children1 {
					child2 <43:6|>
				}
			}
			--------------------------------------------------------------------------------
			14 0 "" -> "jumper "
			43 6 "jumper" -> ""
		'''
	}
	@Test
	def void testMoveElement_3() {

		val fs = new InMemoryURIHandler()
		fs += "inmemory:/file-move3.pstl" -> '''
		#24 direct:
			child:jumper
		'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile("inmemory:/file-move3.pstl", TwoChilds)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.directChild = model.optChild.child
		]
		serializer.endRecordChangesToTextDocuments === '''
			-------------- inmemory:/file-move3.pstl (syntax: <offset|text>) ---------------
			<0:26|#24 direct:
				jumper
			child :>
			--------------------------------------------------------------------------------
			0 26 "#24 direct:\n	chil..." -> "#24 direct:\n	jump..."
		'''
	}

	@Test
	def void testAddElements() {
		val uri = "inmemory:/file-add.pstl"
		val fs = new InMemoryURIHandler()
		fs += uri -> '''
		#23'''

		val rs = fs.createResourceSet
		val model = rs.findFirstOfTypeInFile(uri, ChildWithSubChilds)

		val serializer = newChangeSerializer()
		serializer.addModification(model.eResource) [
			model.children += createChildWithSubChild => [subChilds += createSubChild => [name = "A"]]
			model.children.head => [subChilds += createSubChild => [name = "A2"]]
		]
		serializer.endRecordChangesToTextDocuments === '''
			--------------- inmemory:/file-add.pstl (syntax: <offset|text>) ----------------
			<0:3|#23 subs A A2>
			--------------------------------------------------------------------------------
			0 3 "#23" -> "#23 subs A A2"
		'''
	}
}
