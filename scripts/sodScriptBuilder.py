import sys
sys.path.append('../../devTools/maven')
import scriptBuilder, ProjectParser, depCopy
class sodScriptParameters(scriptBuilder.jacorbParameters):
    def __init__(self, mods):
        scriptBuilder.jacorbParameters.__init__(self)
        for mod in mods: self.update(mod)
        self.name = 'sod'
        homevar = self.add('SOD_HOME', '.', 'initial', 1)
        libvar = self.getVar('LIB', 'initial')
        libvar.setValue(homevar.interp+'/lib')
        self.mainclass = 'edu.sc.seis.sod.Start'

class queryTimer(sodScriptParameters):
    def __init__(self, mods):
        sodScriptParameters.__init__(self, [])
        for mod in mods: self.update(mod)
        self.mainclass='edu.sc.seis.sod.QueryTimer'
        self.name='queryTimer'

class editor(sodScriptParameters):
    def __init__(self, mods):
        sodScriptParameters.__init__(self, [])
        for mod in mods: self.update(mod)
        self.mainclass='edu.sc.seis.sod.editor.SodGUIEditor'
        self.name='editor'

def buildAll(proj):
    scripts = buildSodScripts(proj)
    scripts.extend(buildQueryTimerScripts(proj))
    scripts.extend(buildEditorScripts(proj))
    return scripts

def buildSodScripts(proj):
    scriptBuilder.setVarSh()
    scripts = [scriptBuilder.build(sodScriptParameters([]), proj)]
    scriptBuilder.setVarWindows()
    scripts.append(scriptBuilder.build(sodScriptParameters([scriptBuilder.windowsParameters()]), proj))
    profileParams = sodScriptParameters([scriptBuilder.profileParameters(), scriptBuilder.windowsParameters()])
    profileParams.name='profile'
    scripts.append(scriptBuilder.build(profileParams, proj))
    return scripts

def buildQueryTimerScripts(proj):
    scriptBuilder.setVarSh()
    scripts = [scriptBuilder.build(queryTimer([]), proj)]
    scriptBuilder.setVarWindows()
    scripts.append(scriptBuilder.build(queryTimer([scriptBuilder.windowsParameters()]), proj))
    return scripts

def buildEditorScripts(proj):
    scriptBuilder.setVarSh()
    scripts = [scriptBuilder.build(editor([]), proj)]
    scriptBuilder.setVarWindows()
    scripts.append(scriptBuilder.build(editor([scriptBuilder.windowsParameters()]), proj))
    return scripts

if __name__ == "__main__":
    proj = ProjectParser.ProjectParser('../project.xml')
    depCopy.copy(proj)
    buildAll(proj)
