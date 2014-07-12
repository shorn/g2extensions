package g2idea13

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.plugins.ide.idea.IdeaPlugin


class G2Idea13Plugin implements Plugin<Project> {
  void apply(Project project) {
    project.logger.debug "g2idea13 apply called from $project"

    project.plugins.withType(IdeaPlugin){ IdeaPlugin ideaPlugin ->
      project.logger.debug "idea plugin applied to project: $ideaPlugin"

      G2Idea13ProjectConvention projectConv = project.idea.extensions.create(
        "g2idea13",G2Idea13ProjectConvention)
      projectConv.ideaPlugin = ideaPlugin
      projectConv.project = project
      project.logger.debug "g2idea13 project set to : $project"
    }

    project.afterEvaluate { Project delegateProject ->
      G2Idea13ProjectConvention projectConv = project.idea.extensions.g2idea13
      projectConv.ideaPlugin.model.project.ipr.withXml { provider ->
        if( projectConv.compilerConfiguration ){
          provider.node.component.find { it.@name == 'CompilerConfiguration' }.
            option.@value = projectConv.compilerConfiguration
        }

        if( projectConv.vcs ){
          provider.node.component.find { it.@name == 'VcsDirectoryMappings' }.
            mapping.@vcs = projectConv.vcs
        }

        if( projectConv.vcsDirectory ){
          provider.node.component.find { it.@name == 'VcsDirectoryMappings' }.
            mapping.@directory = projectConv.vcsDirectory
        }
      }

      projectConv.ideaPlugin.model.workspace.iws.withXml { provider ->
        if( projectConv.compilerHeapSize ){
          provider.node.appendNode(
            "component",
            [name:"CompilerWorkspaceConfiguration"]
          ).appendNode(
            "option",
            [ name:"COMPILER_PROCESS_HEAP_SIZE",
              value:projectConv.getCompilerHeapSize() ])
        }
      }
    }
  }

}


class G2Idea13ProjectConvention {
  Project project
  IdeaPlugin ideaPlugin

  String baseDirectory

  String compilerConfiguration
  String vcs
  String vcsDirectory

  String compilerHeapSize

}
