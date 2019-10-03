package yost.uml;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

// https://en.wikipedia.org/wiki/Class_diagram
// https://www.graphviz.org/doc/info
// https://renenyffenegger.ch/notes/tools/Graphviz/examples

public class ClassDiagramExtractor {
	
	private String classFolder = System.getProperty("user.home") + "/git/heronlib/target/classes";
	private File classFolderFile;
	private String filterPackage = "heron";
	private String graphvizFilename = System.getProperty("user.home") + "/heron.gv";
	private List<String> classFiles = new ArrayList<String>();
	private List<String> relations = new ArrayList<String>();
	private ClassLoader classLoader;

	
	public ClassDiagramExtractor() {
		loadClasses();
		String content = createDiagram();
		System.out.println(content);
		writeFile(content, graphvizFilename);		
	}
	
	private void loadClasses() {
		List<URL> urls = new ArrayList<URL>();

		classFolderFile = new File(classFolder);
		classFiles = scanFolder(classFolderFile);
		
		try {
			urls.add(classFolderFile.toURI().toURL());
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		classLoader = new URLClassLoader(urls.toArray(new URL[0]));
	}

	private String createDiagram() {
		String diagram = "";
		List<String> rels;
		String newRelation;
		String lastClassFile = "";
		String packageColor = "";
		int packagei = 0;
		String[] packageColors = new String[] { "blue", "red", "darkgreen", "gold", "purple", "orange", "lightblue", "pink", "magenta", "cyan", "green", "violet" };		// "#F00000"
		
		diagram += "digraph {\n";
		
		// nodes
		for (String classFile : classFiles) {
			if (lastClassFile.isEmpty() || !getPackageName(classFile).equals(getPackageName(lastClassFile))) {
				packageColor = packageColors[packagei % packageColors.length];
				packagei++;
				if (!lastClassFile.isEmpty()) {
					diagram += "}\n";
				}
				diagram += "subgraph cluster_" + getPackageLastName(classFile) + " {\nlabel = <<b>" + getPackageName(classFile) + "</b>>\nstyle=bold\ncolor=" + packageColor + "\n";
			}
			diagram += getTypeName(classFile);
			if (isDeprecated(classFile)) {
				diagram += " [shape=box,style=dashed,color=lightgray]";
			} else if (isInterface(classFile)) {
				diagram += " [shape=diamond,color=" + packageColor + "]";
			} else {
				diagram += " [shape=box,color=" + packageColor + "]";
			}
			diagram += "\n";
			lastClassFile = classFile;
		}
		diagram += "}\n";
		
		// relations (Inheritance)
		relations.clear();
		for (String classFile : classFiles) {
			rels = getInheritances(classFile);
			for (String rel : rels) {
				newRelation = getTypeName(classFile) + " -> " + getTypeName(rel);
				if (!relations.contains(newRelation) && !classFile.equals(rel)) {
					relations.add(newRelation);
				}
			}
		}
		for (String relation : relations) {
			diagram += relation + " [arrowhead=onormal, color=gray]\n";
		}

		// relations (Association)
		relations.clear();
		for (String classFile : classFiles) {
			rels = getProperties(classFile);
			for (String rel : rels) {
				newRelation = getTypeName(classFile) + " -> " + getTypeName(rel);
				if (!relations.contains(newRelation) && !classFile.equals(rel)) {
					relations.add(newRelation);
				}
			}
		}
		for (String relation : relations) {
			diagram += relation + " [arrowhead=vee, color=gray]\n";
		}

		diagram += "}";
		
		return diagram;
	}

	private boolean isInterface(String classFile) {
		Class<?> classContent;
		
		try {
			classContent = classLoader.loadClass(classFile);
			return classContent.isInterface();
		} catch (ClassNotFoundException | NoClassDefFoundError e) {
			System.out.println(e.toString());
		}
		return false;
	}

	private boolean isDeprecated(String classFile) {
		Class<?> classContent;
		Deprecated[] dep;
		
		try {
			classContent = classLoader.loadClass(classFile);
			dep = classContent.getAnnotationsByType(Deprecated.class);
			return (dep.length != 0);
		} catch (ClassNotFoundException | NoClassDefFoundError e) {
			System.out.println(e.toString());
		}
		return false;
	}

	private List<String> getInheritances(String classFile) {
		List<String> inheritances = new ArrayList<String>();
		Class<?> classContent, superClass;
		Class<?>[] interfaces;
		String type;
		
		try {
			classContent = classLoader.loadClass(classFile);
			superClass = classContent.getSuperclass();
			if (superClass != null) {
				type = superClass.getName();
				if (type.contains(filterPackage)) {
					inheritances.add(type);
				}
			}
			interfaces = classContent.getInterfaces();
			for (Class<?> interface0 : interfaces) {
				type = interface0.getName();
				if (type.contains(filterPackage)) {
					inheritances.add(type);
				}
			}
		} catch (ClassNotFoundException | NoClassDefFoundError e) {
			System.out.println(e.toString());
		}
		return inheritances;
	}

	private List<String> getProperties(String classFile) {
		List<String> properties = new ArrayList<String>();
		Class<?> classContent;
		String type;
		Field[] fields;
		
		try {
			classContent = classLoader.loadClass(classFile);
			fields = classContent.getDeclaredFields();
			for (Field field : fields) {
				type = field.getType().getName().replace("$", ".");
				if (type.contains("[")) {
					type = field.getType().getComponentType().getName();
				}
				if (type.contains(filterPackage)) {
					properties.add(type);
				}
			}
		} catch (ClassNotFoundException | NoClassDefFoundError e) {
			System.out.println(e.toString());
		}
		return properties;
	}

	private List<String> scanFolder(File folder) {
		List<String> files = new ArrayList<String>();
		String filepath, packageName, type;
		
		for (File file : folder.listFiles()) {
			if (file.isFile()) {
				filepath = file.getPath();
				if (filepath.endsWith(".class")) {
					packageName = getFilePackage(filepath);
					type = getTypeName(packageName);
					if (!isNumeric(type)) {
						files.add(packageName);
					}
				}
			} else {
				files.addAll(scanFolder(file));		// recursive
			}
		}		
		return files;
	}

	private String getFilePackage(String filepath) {
		String pack = filepath.replace(classFolderFile.getPath(), "").replace("\\", ".").replace("$", ".");
		if (pack.startsWith(".")) {
			pack = pack.substring(1);
		}
		if (pack.toLowerCase().endsWith(".class")) {
			pack = pack.substring(0, pack.length() - 6);
		}
		return pack;
	}
	

	private String getTypeName(String className) {
		String[] parts = className.replace(";", "").split("\\.");
		return parts[parts.length - 1];
	}

	private String getPackageName(String className) {
		String packageName;
		String[] parts = className.split("\\.");
		packageName = parts[0];
		if (parts.length >= 2) {
			packageName += "." + parts[1];
		}
		return packageName;
	}
	
	private String getPackageLastName(String className) {
		String[] parts = className.split("\\.");
		return parts[parts.length - 2];
	}

	private boolean isNumeric(String type) {
		try {
			Integer.parseInt(type);
			return true;
		} catch (Exception e) { }		
		return false;
	}

	/*
	private String readFile(String filename) {
		try {
			return Files.readString(Paths.get(filename));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}
	*/

	private void writeFile(String content, String filename) {
		try {
			Files.writeString(Paths.get(filename), content);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		new ClassDiagramExtractor();
		System.exit(0);
	}

}
