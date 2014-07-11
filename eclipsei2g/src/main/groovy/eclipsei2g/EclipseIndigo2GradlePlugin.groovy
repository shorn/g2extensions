package eclipse2gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class Eclipse2GradlePlugin implements Plugin<Project> {
  void apply(Project project) {
    project.logger.debug "eclipse2gradle apply called from $project"

    project.extensions.create("eclipse2gradle", Eclipse2GProjectConvention)
    project.extensions.eclipse2gradle.project = project
  }
}


class Eclipse2GProjectConvention {
  Project project
  File classpath

  /** simple passthrough by default */
  /*@ClosureParams(String)*/
  Closure<String> modifyPath = { it }

  /** call path modifier on filePath by default */
  /*@ClosureParams(EclipseClasspathLibrary)*/
  Closure<String> generateFilePath = { modifyPath(it.filePath) }

  List<EclipseClasspathLibrary> getLibs(File classpathFile){
    return  Eclipse2GUtils.findClasspathLibs(classpathFile)
  }

  List<EclipseClasspathLibrary> getLibs(){
    assert classpath :
      "must set a classpath file to parse before using this method"
    return  getLibs(classpath)
  }

  List<String> getFilePaths(File classpathFile){
    return Eclipse2GUtils.findClasspathLibs(classpathFile).
      collect{ generateFilePath(it) }
  }

  List<String> getFilePaths(){
    return getFilePaths(classpath)
  }


  /** this will add each eclipse library found to the compile depencies of
   * the project the plugin is applied to
   */
  void addLibsToProjectDependencies(
    /*@ClosureParams(EclipseClasspathLibrary)*/ Closure<Void> c = null
  ) {
    addToCompileDeps(getLibs(), c)
  }

  void addLibsToProjectDependencies(
    String classpath,
    /*@ClosureParams(EclipseClasspathLibrary)*/ Closure<Void> c = null)
  {
    addToCompileDeps(
      Eclipse2GUtils.findClasspathLibs(project.file(classpath)),
      c)
  }

  private void addToCompileDeps(
    List<EclipseClasspathLibrary> classpathLibs,
    Closure<Void> c)
  {
    if( c ){
      classpath.each(c)
    }
    project.dependencies {
      compile project.files(classpathLibs.collect {
        generateFilePath(it)
      })
    }
  }
}


class Eclipse2GUtils {
  static List<EclipseClasspathLibrary> findClasspathLibs(File classpathFile){
    assert classpathFile && classpathFile.canRead()

    // parse the XML .classpath file and extract all library type entries
    def classpathXml = new XmlSlurper().parse(classpathFile)
    def classpathLibs = classpathXml.classpathentry.findAll{ it.@kind == 'lib' }

    // split the libraries up into "direct" and "include"
    def directFiles = classpathLibs.findAll{ it.@including.isEmpty() }
    def includeFiles = classpathLibs.findAll{ !it.@including.isEmpty() }

    List<EclipseClasspathLibrary> libs = []
    // convert from XML node structure to data structure
    libs += directFiles.collect{
      new EclipseClasspathLibrary([path: it.@path.text()])
    }
    libs +=  includeFiles.collect {
      new EclipseClasspathLibrary([
        path: it.@path.text(),
        includePattern: it.@including ])
    }

    return libs
  }
}

class EclipseClasspathLibrary {
  String path
  String includePattern

  /** Returns true if this library is a "direct" file reference.
   * <p/>
   * A classpath xml library entry ("kind=lib") with no "including" attribute
   * is assumed to be pointing directly at an indivifual file (jar, dll, etc.)
   * Note that it may be that the pattern actually specifies an individual
   * filename, in that case this will return false (we do no processing to try
   * to figure out if the pattern matches a single file).
   */
  boolean isDirectEntry(){
    return !includePattern
  }

  String getFilePath() {
    return includePattern ? path + "/" + includePattern : path
  }

  @Override
  public String toString() {
    return filePath
  }
}