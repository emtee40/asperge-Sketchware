package com.iyxan23.asperge.generator.java

import com.iyxan23.asperge.sketchware.models.projectfiles.Project
import com.iyxan23.asperge.sketchware.models.projectfiles.logic.*
import java.lang.StringBuilder

class OldJavaGenerator(
    private val sections: List<BaseLogicSection>,
    private val viewIDs: List<String>,
    private val viewTypes: List<String>,
    project: Project
) {

    private val initialTemplate =
"""package ${project.packageName};

%s
public class %s extends AppCompatActivity {

%s

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindViews();

        // onCreate logic
%s

        registerEvents();
    }

    private void bindViews() {
%s
    }

    private void registerEvents() {
%s
    }

%s
}
"""

    private val globalVariables = ArrayList<Variable>()
    private var classScopeCode = ""
    private var footerCode = ""

    private val events = ArrayList<Event>()
    private val eventsBlocks = HashMap<String, BlocksLogicSection>()
    private var eventsCode = ""

    private val components = ArrayList<Component>()
    private val lists = ArrayList<ListLogic>()

    private var onCreateSection: BlocksLogicSection? = null

    private val neededImports = HashSet<String>().apply { add("androidx.appcompat.app.AppCompatActivity"); add("android.view.*") }

    fun generate(): String {
        var className = "MainActivity"

        sections.forEach { section ->
            className = section.name

            when (section) {
                is VariablesLogicSection    -> globalVariables.addAll(section.variables)
                is EventsLogicSection       -> events.addAll(section.events)
                is ComponentsLogicSection   -> components.addAll(section.components)
                is ListLogicSection         -> lists.addAll(section.lists)

                is BlocksLogicSection -> {
                    if (section.contextName == "java_onCreate_initializeLogic") {
                        onCreateSection = section
                    } else {
                        eventsBlocks[section.contextName] = section
                    }
                }
            }
        }

        classScopeCode += "// Global variables\n"
        globalVariables.forEach { classScopeCode += "private ${genVariableDeclaration(it.type, it.name)}\n" }

        classScopeCode += "// Lists global variables\n"
        lists.forEach { classScopeCode += "private ${genListDeclaration(it.type, it.name)}\n" }

        // components.forEach { variables += "\n// $it" }

        generateViewIdsDeclarations()

        classScopeCode = classScopeCode.trim().prependIndent(" ".repeat(4))

        // Generate codes
        val onCreateCode = generateCode(onCreateSection!!).prependIndent(" ".repeat(8))
        val viewIds = generateViewIds().prependIndent(" ".repeat(8))
        generateEvents(events)
        val imports = generateImports()

        // Then apply it to the initial template
        return initialTemplate.format(
            imports,
            className,
            classScopeCode,
            onCreateCode,
            viewIds,
            eventsCode,
            footerCode
        )
    }

    private fun generateViewIds(): String {
        return StringBuilder().apply {
            viewIDs.forEach { id ->
                append("$id = findViewById(R.id.$id);\n")
            }
        }.toString().trim()
    }

    private fun generateViewIdsDeclarations() {
        classScopeCode += "\n// View IDs declarations\n"
        viewIDs.forEachIndexed { index, id ->
            classScopeCode += "private ${viewTypes[index]} $id;\n"
        }
    }

    private fun generateImports(): String {
        return StringBuilder().apply {
            neededImports.forEach {
                appendLine("import $it;")
            }
        }.toString().trim() + "\n"
    }

    // Used to blacklist blocks that is parsed as a parameter
    private val blacklistedBlocks = ArrayList<String>()

    private fun generateCode(section: BlocksLogicSection, idOffset: Int = -1, addSemicolon: Boolean = true): String {
        val result = StringBuilder()
        var skipOffset = idOffset != -1

        section.blocks.values.forEach { block ->
            if (skipOffset) {
                if (block.id.toInt() == idOffset) skipOffset = false
                else return@forEach
            }

            if (blacklistedBlocks.contains(block.id)) return@forEach

            val parsedParams = ArrayList<String>()

            block.parameters.forEach { param ->
                if (param.startsWith("@")) {
                    // this is a block parameter
                    val blockId = param.substring(1, param.length)
                    parsedParams.add(generateCode(section, blockId.toInt(), false))

                    blacklistedBlocks.add(blockId)

                } else {
                    parsedParams.add(param)
                }
            }

            result.appendLine(BlocksDictionary.generateCode(block.opCode, parsedParams, block.spec, addSemicolon = addSemicolon))
        }

        return result.toString().trim()
    }

    private fun genVariableDeclaration(type: Int, name: String): String {
        return when (type) {
            0 -> "boolean $name = false;"
            1 -> "int $name = 0;"
            2 -> "String $name = \"\";"
            3 -> "HashMap<String, Object> $name = new HashMap();"
            else -> "// Unknown variable type $type"
        }
    }

    private fun genListDeclaration(type: Int, name: String): String {
        return when (type) {
            0 -> "ArrayList<Boolean> $name = new ArrayList();"
            1 -> "ArrayList<Integer> $name = new ArrayList();"
            2 -> "ArrayList<String> $name = new ArrayList();"
            3 -> "ArrayList<HashMap<String, Object>> $name = new ArrayList();"
            else -> "// Unknown variable type $type"
        }
    }

    private fun generateEvents(events: List<Event>) {
        events.forEach { generateEvent(it) }
    }

    private fun generateEvent(event: Event) {
        val blocks = eventsBlocks["java_${event.targetId}_${event.eventName}"]

        if (blocks == null) {
            eventsCode += "// Cannot find blocks of $event".prependIndent(" ".repeat(8))
            return
        }

        when (event.eventName) {
            "onClick" ->
                eventsCode +=
"""
${event.targetId}.setOnClickListener(new View.OnClickListener() {
${generateCode(blocks).prependIndent(" ".repeat(4))}
});
""".prependIndent(" ".repeat(8))

            else -> eventsCode += """// Unknown event ${event.eventName}"""
        }
    }
}