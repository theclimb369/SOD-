#! /usr/bin/python -O

import buildSod
import sys, os, time, zipfile, tarfile
sys.path.append("../devTools/maven")
sys.path.append("./scripts")
import distBuilder, buildSodScripts, ProjectParser
from optparse import OptionParser

def buildInternal(proj, name):
    if name == buildName(proj): name = "internal" + name
    scripts=buildSodScripts.buildAll(proj)
    configs = []
    for item in os.listdir('scripts'):
        if item.endswith('.xml'): configs.append(item)
    extras = [('scripts/' + item, 'bin/' + item) for item in configs]
    extras.extend([('scripts/yjpagent.dll', 'yjpagent.dll'),
              ('scripts/cwg.prop', 'cwg.prop'),
              ('scripts/logs', 'logs', False)])
    buildDist(proj, scripts, name, extras)

def buildExternal(proj, name):
    scripts=buildSodScripts.buildSodScripts(proj)
    scripts.extend(buildSodScripts.buildEditorScripts(proj))
    os.chdir('site')
    print 'building docs'
    os.spawnlp(os.P_WAIT, 'buildSite.sh', 'sh', 'buildSite.sh')
    os.chdir('..')
    extras = [('scripts/tutorial.xml', 'docs/tutorial.xml'),
              ('scripts/weed.xml', 'docs/weed.xml'),
              ('site/generatedSite', 'docs')]
    zip = zipfile.ZipFile(name + ".zip", 'w')
    tar = tarfile.open(name + '.tar', 'w')
    buildDist(proj, scripts, name, extras, [tar, zip])

def buildDist(proj, scripts, name=None, extras=[], archives=[]):
    buildSod.build(proj)
    if not os.path.exists('scripts/logs'): os.mkdir('scripts/logs')
    extras.extend([(script, 'bin/'+script) for script in scripts])
    if name is None: name = buildName(proj)
    distBuilder.buildDist(proj, extras, name, True, archives)
    for script in scripts: os.remove(script)

def buildName(proj): return proj.name + '-' + time.strftime('%y%m%d')

if __name__ == "__main__":
    proj = ProjectParser.ProjectParser('./project.xml')
    parser = OptionParser()
    parser.add_option("-n", "--name", dest="name",
                      help="tar base name", metavar="NAME",
                      default=buildName(proj))
    parser.add_option("-e", "--external", dest="external",
                      help="build external dist", default=False,
                      action="store_true")
    options = parser.parse_args()[0]
    if options.external : buildExternal(proj, options.name)
    else: buildInternal(proj, options.name)
