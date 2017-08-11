package org.sbfc.converter.sbml2sbgnml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.lang.Math;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.sbfc.converter.GeneralConverter;
import org.sbfc.converter.exceptions.ConversionException;
import org.sbfc.converter.exceptions.ReadModelException;
import org.sbfc.converter.models.GeneralModel;
import org.sbfc.converter.models.SBGNModel;
import org.sbfc.converter.models.SBMLModel;
import org.sbfc.converter.utils.sbgn.SBGNUtils;
import org.sbgn.SbgnUtil;
import org.sbgn.bindings.Arc;
import org.sbgn.bindings.Arcgroup;
import org.sbgn.bindings.Bbox;
import org.sbgn.bindings.Glyph;
import org.sbgn.bindings.Port;
import org.sbgn.bindings.SBGNBase;
import org.sbgn.bindings.Sbgn;
import org.sbgn.bindings.SBGNBase.Extension;
import org.sbml.jsbml.CVTerm;
import org.sbml.jsbml.CVTerm.Qualifier;
import org.sbml.jsbml.Constraint;
import org.sbml.jsbml.FunctionDefinition;
import org.sbml.jsbml.InitialAssignment;
import org.sbml.jsbml.ListOf;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.ModifierSpeciesReference;
import org.sbml.jsbml.Parameter;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.Rule;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBMLException;
import org.sbml.jsbml.SBase;
import org.sbml.jsbml.SimpleSpeciesReference;
import org.sbml.jsbml.Species;
import org.sbml.jsbml.SpeciesReference;
import org.sbml.jsbml.UnitDefinition;
import org.sbml.jsbml.ext.layout.BoundingBox;
import org.sbml.jsbml.ext.layout.CompartmentGlyph;
import org.sbml.jsbml.ext.layout.Curve;
import org.sbml.jsbml.ext.layout.CurveSegment;
import org.sbml.jsbml.ext.layout.Dimensions;
import org.sbml.jsbml.ext.layout.GeneralGlyph;
import org.sbml.jsbml.ext.layout.GraphicalObject;
import org.sbml.jsbml.ext.layout.ReactionGlyph;
import org.sbml.jsbml.ext.layout.ReferenceGlyph;
import org.sbml.jsbml.ext.layout.SpeciesGlyph;
import org.sbml.jsbml.ext.layout.SpeciesReferenceGlyph;
import org.sbml.jsbml.ext.layout.SpeciesReferenceRole;
import org.sbml.jsbml.ext.layout.TextGlyph;
import org.sbml.jsbml.ext.qual.FunctionTerm;
import org.sbml.jsbml.ext.qual.QualitativeSpecies;
import org.sbml.jsbml.ext.qual.Transition;
import org.sbml.jsbml.ext.render.RenderConstants;
import org.sbml.jsbml.ext.render.RenderGraphicalObjectPlugin;
import org.xml.sax.SAXException;

public class SBML2SBGNML_GSOC2017 extends GeneralConverter { 
	
	private static Logger logger;
	public SBML2SBGNMLUtil sUtil;
	public SBML2SBGNMLOutput sOutput;
	public SWrapperMap sWrapperMap;
			
	/**
	 * Initialize the converter with a SBML2SBGNMLUtil and a SBML2SBGNMLOutput
	 * 
	 * @param <code>SBMLDocument</code> sbmlDocument
	 */	
	public SBML2SBGNML_GSOC2017(SBMLDocument sbmlDocument) {
		logger = Logger.getLogger(SBML2SBGNML_GSOC2017.class);
		
		sUtil = new SBML2SBGNMLUtil();
		sOutput = new SBML2SBGNMLOutput(sbmlDocument);
		sWrapperMap = new SWrapperMap(sOutput.map, sOutput.sbmlModel);
	}
	
	/**
	 * Create an <code>Sbgn</code>, which corresponds to an SBML <code>Model</code>. 
	 * Each element in the <code>Model</code> is mapped to an SBGN <code>Glyph</code>.
	 * 
	 * @param <code>SBMLDocument</code> sbmlDocument
	 * @return <code>Sbgn</code> sbgnObject
	 */			
	public Sbgn convertToSBGNML(SBMLDocument sbmlDocument) throws SBMLException {
	
		if (sOutput.sbmlLayoutModel != null){
			// note: the order of execution matters
			createFromCompartmentGlyphs(sOutput.sbgnObject, sOutput.listOfCompartmentGlyphs);
			createFromSpeciesGlyphs(sOutput.sbgnObject, sOutput.listOfSpeciesGlyphs);
			createFromGeneralGlyphs(sOutput.sbgnObject, sOutput.listOfAdditionalGraphicalObjects);
			createLabelsFromTextGlyphs(sOutput.sbgnObject, sOutput.listOfTextGlyphs);
			createFromReactionGlyphs(sOutput.sbgnObject, sOutput.listOfReactionGlyphs);	
			
			addedChildGlyphs();
			
			for (String key: sWrapperMap.listOfSWrapperArcs.keySet()) {
				SWrapperArc sWrapperArc = sWrapperMap.listOfSWrapperArcs.get(key);
				addPortForArc(sWrapperArc.speciesReferenceGlyph, sWrapperArc.arc);
			}
			
		}
		
		createExtensionsForMathML();
		
		if (sOutput.listOfQualitativeSpecies != null){
			createExtensionsForQualMathML();
		}
		
		// return one sbgnObject
		return sOutput.sbgnObject;		
	}
	
	private void addedChildGlyphs() {
		for (String key : sWrapperMap.notAdded.keySet()){
			
			String parentKey = sWrapperMap.notAdded.get(key);
			Glyph parentGlyph = sWrapperMap.getGlyph(parentKey);
			Glyph glyph = sWrapperMap.getGlyph(key);
			
			parentGlyph.getGlyph().add(glyph);
			System.out.println("addedChildGlyphs "+glyph.getId() +" in "+parentGlyph.getId());
		}
		
	}

	public void createExtensionsForMathML(){
		ListOf<FunctionDefinition> listOfFunctionDefinitions = null;
		ListOf<UnitDefinition> listOfUnitDefinitions = null;
		ListOf<Parameter> listOfParameters = null;
		ListOf<InitialAssignment> listOfInitialAssignments = null;
		ListOf<Rule> listOfRules = null;
		ListOf<Constraint> listOfConstraints = null;
		
		listOfUnitDefinitions = sOutput.listOfUnitDefinitions;
		listOfParameters = sOutput.listOfParameters;
		// these contain math
		listOfFunctionDefinitions = sOutput.listOfFunctionDefinitions;
		listOfInitialAssignments = sOutput.listOfInitialAssignments;
		listOfRules = sOutput.listOfRules;
		listOfConstraints = sOutput.listOfConstraints;
		
		for (UnitDefinition ud: listOfUnitDefinitions){
			sUtil.addSbaseInExtension(sOutput.sbgnObject, ud);
		}
		
		// todo add the rest
			
	}
	
	public void createExtensionsForQualMathML(){
		ListOf<QualitativeSpecies> listOfQualitativeSpecies = null;
		ListOf<Transition> listOfTransitions = null;
		listOfTransitions = sOutput.listOfTransitions;
		
		for (Transition tr: listOfTransitions){
			for (FunctionTerm ft: tr.getListOfFunctionTerms()){
				sUtil.addMathMLInExtension(sOutput.sbgnObject, tr, ft);
			}
		}		
	}
		
	/**
	 * Create multiple SBGN <code>Glyph</code>, each corresponding to an SBML <code>CompartmentGlyph</code>. 
	 * TODO: many things still need to be handled, see comments below
	 * 
	 * @param <code>Sbgn</code> sbgnObject
	 * @param <code>ListOf<CompartmentGlyph></code> listOfCompartmentGlyphs
	 */			
	public void createFromCompartmentGlyphs(Sbgn sbgnObject, ListOf<CompartmentGlyph> listOfCompartmentGlyphs) {
		Glyph sbgnCompartmentGlyph;
		
		if (listOfCompartmentGlyphs == null){return;}
		
		for (CompartmentGlyph compartmentGlyph : listOfCompartmentGlyphs){
			sbgnCompartmentGlyph = createFromOneCompartmentGlyph(sbgnObject, compartmentGlyph);
			
			// add the created Glyph to the output
			sOutput.addGlyphToMap(sbgnCompartmentGlyph);
		}
		
	}
	
	public Glyph createFromOneCompartmentGlyph(Sbgn sbgnObject, CompartmentGlyph compartmentGlyph){
		Glyph sbgnCompartmentGlyph;
		
		String clazz = sUtil.sbu.getOutputFromClass(compartmentGlyph.getCompartmentInstance(), "compartment");
		if (clazz.equals("compartment")){
			clazz = sUtil.sbu.getOutputFromClass(compartmentGlyph, "compartment");
		}
		
		// create a new Glyph, set its Bbox, but don't set a Label
		sbgnCompartmentGlyph = sUtil.createGlyph(compartmentGlyph.getId(), clazz, 
				true, compartmentGlyph, 
				false, compartmentGlyph.getCompartment());	
		
		// todo: set label?
		// todo: create Auxiliary items?
		// todo: need to keep compartment name
		
		try {
			sUtil.addAnnotationInExtension(sbgnCompartmentGlyph, compartmentGlyph.getAnnotation());
			sUtil.addAnnotationInExtension(sbgnCompartmentGlyph, compartmentGlyph.getCompartmentInstance().getAnnotation());
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return sbgnCompartmentGlyph;
	}
	
	/**
	 * Create multiple SBGN <code>Glyph</code>, each corresponding to an SBML <code>SpeciesGlyph</code>. 
	 * TODO: many things still need to be handled, see comments below
	 * 
	 * @param <code>Sbgn</code> sbgnObject
	 * @param <code>ListOf<SpeciesGlyph></code> listOfSpeciesGlyphs
	 */		
	public void createFromSpeciesGlyphs(Sbgn sbgnObject, ListOf<SpeciesGlyph> listOfSpeciesGlyphs) {
		SWrapperGlyphEntityPool sbgnSpeciesGlyph;
		
		if (listOfSpeciesGlyphs == null){return;}
		
		for (SpeciesGlyph speciesGlyph : listOfSpeciesGlyphs){
			sbgnSpeciesGlyph = createFromOneSpeciesGlyph(sbgnObject, speciesGlyph);
			
			sWrapperMap.listOfSWrapperGlyphEntityPools.put(sbgnSpeciesGlyph.id, sbgnSpeciesGlyph);
			
			
			// todo: same for general glyphs
			boolean hasParent = false;
			List<CVTerm> cvTerms = sbgnSpeciesGlyph.species.getAnnotation().getListOfCVTerms();
			System.out.println("-==? size" + cvTerms.size());
			
			for (CVTerm cvt : cvTerms){
				System.out.println("-==?");
				System.out.println(cvt.getBiologicalQualifierType().getElementNameEquivalent());
				System.out.println(Qualifier.BQB_IS_PART_OF.getElementNameEquivalent());
				if (cvt.getBiologicalQualifierType().getElementNameEquivalent().equals(Qualifier.BQB_IS_PART_OF.getElementNameEquivalent())){
					String parent = cvt.getResources().get(0);
					hasParent = true;
					sWrapperMap.notAdded.put(speciesGlyph.getSpecies(), parent.split("_")[1]);
					System.out.println("-==? hasParent "+ parent);
				}
				
			}
			if (!hasParent){
				sOutput.addGlyphToMap(sbgnSpeciesGlyph.glyph);				
			}
		}
	}
	
	public SWrapperGlyphEntityPool createFromOneSpeciesGlyph(Sbgn sbgnObject, SpeciesGlyph speciesGlyph){
		Glyph sbgnSpeciesGlyph;
		SWrapperGlyphEntityPool sWrapperGlyphEntityPool = null;
		
		RenderGraphicalObjectPlugin renderGraphicalObjectPlugin = (RenderGraphicalObjectPlugin) speciesGlyph.getPlugin(RenderConstants.shortLabel);
		String objectRole = renderGraphicalObjectPlugin.getObjectRole();
		String clazz = mapObjectRoleToClazz(objectRole);
		
		// create a new Glyph, set its Bbox, set a Label
		// todo: or clazz could be simple chemical etc.
		if (clazz.equals("unspecified entity")){
			clazz = sUtil.sbu.getOutputFromClass(speciesGlyph.getSpeciesInstance(), "unspecified entity");
		}
		if (clazz.equals("unspecified entity")){
			clazz = sUtil.sbu.getOutputFromClass(speciesGlyph, "unspecified entity");
		}
		
		String orientation = null;
		if (clazz.contains("tag")){
			String[] array = clazz.split("_");
			clazz = array[0];
			orientation = array[1];
		}
		
		sbgnSpeciesGlyph = sUtil.createGlyph(speciesGlyph.getId(), clazz, 
				true, speciesGlyph, 
				false, speciesGlyph.getSpecies());	
		
		if (orientation != null){
			sbgnSpeciesGlyph.setOrientation(orientation);
		}
		
		
		System.out.println("speciesGlyph.getSpecies() "+speciesGlyph.getSpecies() + " clazz= " + clazz);
		
		// todo: create Auxiliary items?
		
		if (speciesGlyph.getSpeciesInstance() instanceof Species){
			sWrapperGlyphEntityPool = new SWrapperGlyphEntityPool(sbgnSpeciesGlyph, 
					(Species) speciesGlyph.getSpeciesInstance(), speciesGlyph);
		} else if (speciesGlyph.getSpeciesInstance() instanceof QualitativeSpecies){
			sWrapperGlyphEntityPool = new SWrapperGlyphEntityPool(sbgnSpeciesGlyph, 
					(QualitativeSpecies) speciesGlyph.getSpeciesInstance(), speciesGlyph);
		}
		
		try {
			sUtil.addAnnotationInExtension(sbgnSpeciesGlyph, speciesGlyph.getAnnotation());
			sUtil.addAnnotationInExtension(sbgnSpeciesGlyph, speciesGlyph.getSpeciesInstance().getAnnotation());
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return sWrapperGlyphEntityPool;
	}	
	
	private String mapObjectRoleToClazz(String objectRole) {
		String clazz = null;
		
		// todo: horizontal or vertical?
		if (objectRole.equals("or")){
			clazz = "or";
		} else if (objectRole.equals("and")){
			clazz = "and";
		} else if (objectRole.equals("not")){
			clazz = "not";
		} 
		
		else if (objectRole.equals("SBO0000167")){
			clazz = "process";
		} else if (objectRole.equals("SBO0000177")){
			clazz = "association";
		} else if (objectRole.equals("SBO0000180")){
			clazz = "dissociation";
		} else if (objectRole.equals("SBO0000397")){
			clazz = "omitted process";
		} else if (objectRole.equals("SBO0000396")){
			clazz = "uncertain process";
		}
		
		else if (objectRole.equals("SBO0000245")){
			clazz = "macromolecule";
		} else if (objectRole.equals("SBO0000247")){
			clazz = "simple chemical";
		} else if (objectRole.equals("SBO0000291")){
			clazz = "source and sink";
		} else if (objectRole.equals("SBO0000354")){
			clazz = "nucleic acid feature";
		} else if (objectRole.equals("SBO0000253")){
			clazz = "complex";
		} else if (objectRole.equals("SBO0000405")){
			clazz = "perturbing agent";
		 }else if (objectRole.equals("SBO0000285")){
			clazz = "unspecified entity";
		}
		
		else if (objectRole.equals("SBO0000358")){
			clazz = "phenotype";
		} else if (objectRole.equals("SBO0000358")){
			//biological activity
		} else if (objectRole.equals("")){
			//annotation
		} 
		
		else if (objectRole.equals("SBO0000247clone")){
			clazz = "simple chemical";
		} else if (objectRole.equals("SBO0000245clone")){
			clazz = "macromolecule";
		} else if (objectRole.equals("SBO0000354clone")){
			clazz = "nucleic acid feature";
		} 
		
		else if (objectRole.equals("SBO0000245multimerclone")){
			clazz = "macromolecule multimer";
		} else if (objectRole.equals("SBO0000247multimerclone")){
			clazz = "simple chemical multimer";
		} else if (objectRole.equals("SBO0000354multimerclone")){
			clazz = "nucleic acid feature multimer";
		} 
		
		else if (objectRole.equals("SBO0000247multimer")){
			clazz = "simple chemical multimer";
		} else if (objectRole.equals("SBO0000245multimer")){
			clazz = "macromolecule multimer";
		} else if (objectRole.equals("SBO0000354multimer")){
			clazz = "nucleic acid feature multimer";
		} else if (objectRole.equals("SBO0000253multimer")){
			clazz = "complex multimer";
		}
		
		else if (objectRole.equals("unitofinfo")){
			System.out.println("===>unitofinfo");
			clazz = "unit of information";
		} else if (objectRole.equals("unitofinfo")){
			clazz = "cardinality";
		} else if (objectRole.equals("statevar")){
			clazz = "state variable";
		} 
		
		else if (objectRole.equals("SBO0000289")){
			clazz = "compartment";
		} else if (objectRole.equals("SBO0000395")){
			clazz = "submap";
		} 
		
		else if (objectRole.equals("Tagleft")){
			clazz = "tag_left";
		} else if (objectRole.equals("Tagright")){
			clazz = "tag_right";
		} else if (objectRole.equals("Tagup")){
			clazz = "tag_up";
		} else if (objectRole.equals("Tagdown")){
			clazz = "tag_down";
		}
		
		else if (objectRole.equals("Tagleft")){
			clazz = "terminal_left";
		} else if (objectRole.equals("Tagright")){
			clazz = "terminal_right";
		} else if (objectRole.equals("Tagup")){
			clazz = "terminal_up";
		} else if (objectRole.equals("Tagdown")){
			clazz = "terminal_down";
		}		
		
		
		if (objectRole.equals("SBO0000172")){
			clazz = "catalysis";
		} else if (objectRole.equals("product")){
			clazz = "production";
		} else if (objectRole.equals("substrate")){	
			clazz = "consumption";
		} else if (objectRole.equals("SBO0000170")){
			clazz = "stimulation";
		} else if (objectRole.equals("SBO0000171")){
			clazz = "necessary stimulation";
		} else if (objectRole.equals("SBO0000168")){
			clazz = "unknown influence";
		} else if (objectRole.equals("SBO0000169")){
			clazz = "inhibition";
		} else if (objectRole.equals("SBO0000168")){
			clazz = "modulation";
		}
		
		else if (objectRole.equals("equivalence")){
			clazz = "equivalence arc";
		} else if (objectRole.equals("SBO0000398")){
			clazz = "logic arc";
		}
		
		if (clazz == null){
			return "unspecified entity";
		} else {
			return clazz;
		}
		
	}

	/**
	 * Create multiple SBGN <code>Glyph</code>, each corresponding to an SBML <code>ReactionGlyph</code>. 
	 * 
	 * @param <code>Sbgn</code> sbgnObject
	 * @param <code>ListOf<ReactionGlyph></code> listOfReactionGlyphs
	 */			
	public void createFromReactionGlyphs(Sbgn sbgnObject, ListOf<ReactionGlyph> listOfReactionGlyphs) {
		SWrapperArcGroup sbgnReactionGlyph;
		SWrapperGlyphProcess sWrapperGlyphProcess;

		if (listOfReactionGlyphs == null){return;}
		
		for (ReactionGlyph reactionGlyph : listOfReactionGlyphs){
			// todo: return an ArcGroup instead
			sbgnReactionGlyph = createFromOneReactionGlyph(sbgnObject, reactionGlyph);
			
			sWrapperGlyphProcess = new SWrapperGlyphProcess(sbgnReactionGlyph, reactionGlyph, 
										(Reaction) reactionGlyph.getReactionInstance(), null, 
										// the first Glyph is the Process Node
										sbgnReactionGlyph.arcGroup.getGlyph().get(0));
			
			System.out.println("====>>>>>createFromReactionGlyphs "+sbgnReactionGlyph.arcGroup.getGlyph().get(0).getId());
			
			
			sWrapperMap.listOfSWrapperGlyphProcesses.put(sbgnReactionGlyph.reactionId, sWrapperGlyphProcess);
			//System.out.println("sbgnReactionGlyph.reactionId "+sbgnReactionGlyph.reactionId);
			
		}		
	}
	
	/**
	 * Create SBGN <code>Glyph</code> and <code>Arc</code>, corresponding to an SBML <code>ReactionGlyph</code>. 
	 * TODO: many things still need to be handled, see comments below
	 * 
	 * @param <code>Sbgn</code> sbgnObject
	 * @param <code>ReactionGlyph</code> reactionGlyph
	 */		
	public SWrapperArcGroup createFromOneReactionGlyph(Sbgn sbgnObject, ReactionGlyph reactionGlyph) {
		Arcgroup processNode = null;
		Curve sbmlCurve;
		ListOf<SpeciesReferenceGlyph> listOfSpeciesReferenceGlyphs;
		Reaction reaction;
		SWrapperArcGroup sWrapperArcGroup = null;
		
		if (reactionGlyph.isSetReaction()) {
			System.out.println("here");
			// create a process node from dimensions of the curve
			// todo: change to more robust method
			// todo: create Auxiliary items?	
			if (reactionGlyph.isSetCurve()) {
				RenderGraphicalObjectPlugin renderGraphicalObjectPlugin = (RenderGraphicalObjectPlugin) reactionGlyph.getPlugin(RenderConstants.shortLabel);
				String objectRole = renderGraphicalObjectPlugin.getObjectRole();
				String clazz = mapObjectRoleToClazz(objectRole);
				
				if (clazz.equals("unspecified entity")){
					sUtil.sbu.getOutputFromClass(reactionGlyph.getReactionInstance(), "process");
				}
				if (clazz.equals("process")){
					clazz = sUtil.sbu.getOutputFromClass(reactionGlyph, "process");
				}
				
				sbmlCurve = reactionGlyph.getCurve();
				processNode = sUtil.createOneProcessNode(reactionGlyph.getReaction(), sbmlCurve, clazz);
				
				// for now, set style to just a Bbox
				sUtil.createBBox(reactionGlyph, processNode.getGlyph().get(0));
				processNode.getArc().set(0, null);
				
				
				sOutput.addArcgroupToMap(processNode);
				sWrapperArcGroup = new SWrapperArcGroup(reactionGlyph.getReaction(), processNode);
				//System.out.println("reactionGlyph.getReaction() "+reactionGlyph.getReaction());
			}
			
			listOfSpeciesReferenceGlyphs = reactionGlyph.getListOfSpeciesReferenceGlyphs();
			if (listOfSpeciesReferenceGlyphs.size() > 0) {
				
				createFromSpeciesReferenceGlyphs(listOfSpeciesReferenceGlyphs, processNode.getGlyph().get(0));
			}	
			
			// store any additional information into SBGN
			reaction = (Reaction) reactionGlyph.getReactionInstance();
			if (reaction.getKineticLaw() != null && reactionGlyph.isSetCurve()) {

				String math = reaction.getKineticLaw().getMathMLString();
				sUtil.addExtensionElement(processNode, math);
			}
		}
		
		try {
			sUtil.addAnnotationInExtension(processNode, reactionGlyph.getAnnotation());
			sUtil.addAnnotationInExtension(processNode, reactionGlyph.getReactionInstance().getAnnotation());
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return sWrapperArcGroup;
	}
	
	public void createFromSpeciesReferenceGlyphs(ListOf<SpeciesReferenceGlyph> listOfSpeciesReferenceGlyphs, Glyph reactionGlyph) {
		Arc arc;
		SWrapperArc sWrapperArc;
		
		if (listOfSpeciesReferenceGlyphs == null){return;}
		
		for (SpeciesReferenceGlyph speciesReferenceGlyph : listOfSpeciesReferenceGlyphs){
			sUtil.printHelper("createGlyphFromReactionGlyph", 
					String.format("speciesGlyph = %s, speciesReference = %s \n", 
					speciesReferenceGlyph.getSpeciesGlyph(), speciesReferenceGlyph.getSpeciesReference()));
			
			sWrapperArc = createFromOneSpeciesReferenceGlyph(speciesReferenceGlyph, reactionGlyph);
			// store the created Arc into SBGN
			sOutput.addArcToMap(sWrapperArc.arc);	
			
			sWrapperMap.listOfSWrapperArcs.put(sWrapperArc.id, sWrapperArc);
		}		
	}
	
	public SWrapperArc createFromOneSpeciesReferenceGlyph(SpeciesReferenceGlyph speciesReferenceGlyph, Glyph reactionGlyph){
		Arc arc;
		Curve sbmlCurve;
		SWrapperArc sWrapperArc = null;
		
		sbmlCurve = speciesReferenceGlyph.getCurve();
		
		// create an Arc for the SpeciesReferenceGlyph
		// todo: need source/target glyphs without violating syntax? i.e process nodes always connect arcs?
		// can't do need bezier, libSBGN doesn't have it
		// need port
		arc = sUtil.createOneArc(sbmlCurve);
		
		RenderGraphicalObjectPlugin renderGraphicalObjectPlugin = (RenderGraphicalObjectPlugin) speciesReferenceGlyph.getPlugin(RenderConstants.shortLabel);
		String objectRole = renderGraphicalObjectPlugin.getObjectRole();
		String clazz = mapObjectRoleToClazz(objectRole);
		
		// set Clazz of the Arc
		// todo: need to determine from getOutputFromClass between production/consumption etc			
		if (clazz.equals("unspecified entity")){
			if (speciesReferenceGlyph.getSpeciesReferenceRole() != null){
				clazz = sUtil.searchForReactionRole(speciesReferenceGlyph.getSpeciesReferenceRole());
			}
			
		}
		if (clazz == null){
			clazz = sUtil.sbu.getOutputFromClass(speciesReferenceGlyph, "unknown influence");
		}
		if (clazz.equals("unknown influence")){
			clazz = sUtil.sbu.getOutputFromClass(speciesReferenceGlyph.getReferenceInstance(), "unknown influence");
		}
		arc.setClazz(clazz);
				
		SimpleSpeciesReference simpleSpeciesReference = (SimpleSpeciesReference) speciesReferenceGlyph.getSpeciesReferenceInstance();
		if (simpleSpeciesReference instanceof SpeciesReference){
			sWrapperArc = new SWrapperArc(arc, speciesReferenceGlyph,
					(SpeciesReference) simpleSpeciesReference);			
		} else if (simpleSpeciesReference instanceof ModifierSpeciesReference) {
			sWrapperArc = new SWrapperArc(arc, speciesReferenceGlyph,
					(ModifierSpeciesReference) simpleSpeciesReference);			
		}

		String sourceTargetType;
		String reactionId = speciesReferenceGlyph.getSpeciesReferenceInstance().getParentSBMLObject().getParentSBMLObject().getId();
		//System.out.println("reactionId: "+ reactionId);
		
		String speciesId = null;
		Glyph glyph = null;
		try{
		speciesId = speciesReferenceGlyph.getSpeciesGlyphInstance().getSpecies();
		glyph = sWrapperMap.listOfSWrapperGlyphEntityPools.get(speciesId).glyph;
		} catch (Exception e){}
		
		if (clazz.equals("production")){
			sourceTargetType="reactionToSpecies";

			Port port = new Port();
			port.setId("Port" + "_" + reactionId + "_" + speciesId);
			port.setX(arc.getStart().getX());
			port.setY(arc.getStart().getY());
			reactionGlyph.getPort().add(port);
			arc.setSource(port);
			
			arc.setTarget(glyph);
			
		}else{
			sourceTargetType="speciesToReaction";

			Port port = new Port();
			port.setId("Port" + "_" + speciesId + "_" + reactionId);
			port.setX(arc.getEnd().getX());
			port.setY(arc.getEnd().getY());
			reactionGlyph.getPort().add(port);
			arc.setTarget(port);
			
			arc.setSource(glyph);
		}
		sWrapperArc.setSourceTarget(reactionId, speciesId, sourceTargetType);
				
		try {
			sUtil.addAnnotationInExtension(arc, speciesReferenceGlyph.getAnnotation());
			sUtil.addAnnotationInExtension(arc, speciesReferenceGlyph.getReferenceInstance().getAnnotation());
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
		return sWrapperArc;
	}
	
	/**
	 * Create multiple SBGN <code>Label</code>, each corresponding to an SBML <code>TextGlyph</code>. 
	 * 
	 * @param <code>Sbgn</code> sbgnObject
	 * @param <code>ListOf<TextGlyph></code> listOfTextGlyphs
	 */			
	public void createLabelsFromTextGlyphs(Sbgn sbgnObject, ListOf<TextGlyph> listOfTextGlyphs) {
		
		if (listOfTextGlyphs == null){return;}
		
		for (TextGlyph textGlyph : listOfTextGlyphs){	
			createLabelFromOneTextGlyph(textGlyph);
		}
	}
	
	public void createLabelFromOneTextGlyph(TextGlyph textGlyph) {
		Glyph sbgnGlyph;
		String id = null;
		String text;
		List<Glyph> listOfGlyphs;
		int indexOfSpeciesGlyph;		
		
		if (textGlyph.isSetText()) {
			text = textGlyph.getText();
		} else if (textGlyph.isSetOriginOfText()) {
			// todo: don't get the reference, get the text instead
			text = textGlyph.getOriginOfText();
		} else {
			text = "";
		}
		
		if (textGlyph.isSetGraphicalObject()) {
			id = textGlyph.getGraphicalObjectInstance().getId();
			listOfGlyphs = sOutput.sbgnObject.getMap().getGlyph();
			
			// find the Glyph that should contain this text
			indexOfSpeciesGlyph = sUtil.searchForIndex(listOfGlyphs, id);
			if (indexOfSpeciesGlyph != -1){
				sbgnGlyph = listOfGlyphs.get(indexOfSpeciesGlyph);
				sUtil.setLabel(sbgnGlyph, text);
			}

			
		} else {
			// clazz is unknown
			sbgnGlyph = sUtil.createGlyph(textGlyph.getId(), "unspecified entity", false, null, true, text);
			sUtil.createVoidBBox(sbgnGlyph);
			// add this new glyph to Map
			sOutput.addGlyphToMap(sbgnGlyph);		
					
		}		
	}
	
	/**
	 * Create multiple SBGN <code>Glyph</code>, corresponding to SBML <code>GeneralGlyph</code>. 
	 * Each <code>GraphicalObject</code> is casted to <code>GeneralGlyph</code>. 
	 * 
	 * @param <code>Sbgn</code> sbgnObject
	 * @param <code>ListOf<GraphicalObject></code> listOfAdditionalGraphicalObjects
	 */		
	public void createFromGeneralGlyphs(Sbgn sbgnObject, ListOf<GraphicalObject> listOfAdditionalGraphicalObjects) {
		GeneralGlyph generalGlyph;
		BoundingBox bbox;
		Arcgroup arcgroup;
		
		if (listOfAdditionalGraphicalObjects == null){return;}
		
		// treat each GeneralGlyph like a ReactionGlyph
		for (GraphicalObject graphicalObject : listOfAdditionalGraphicalObjects) {
			bbox = graphicalObject.getBoundingBox();
			generalGlyph = (GeneralGlyph) graphicalObject;

			arcgroup = createFromOneGeneralGlyph(sbgnObject, generalGlyph);
			sOutput.addArcgroupToMap(arcgroup);
		}
	}
	
	/**
	 * Create multiple SBGN <code>Glyph</code> from an SBML <code>GeneralGlyph</code>. 
	 * TODO: handle isSetCurve and isSetListOfSubGlyphs. Iterate the listOfSubGlyphs.
	 * 
	 * @param <code>Sbgn</code> sbgnObject
	 * @param <code>ListOf<GraphicalObject></code> listOfAdditionalGraphicalObjects
	 */		
	public Arcgroup createFromOneGeneralGlyph(Sbgn sbgnObject, GeneralGlyph generalGlyph){
		ListOf<ReferenceGlyph> listOfReferenceGlyphs;
		ListOf<GraphicalObject> listOfSubGlyphs;	
		
		List<Glyph> listOfGlyphs = new ArrayList<Glyph>();
		List<Arc> listOfArcs = new ArrayList<Arc>();
		Glyph glyph;
		Arc arc;
		Arcgroup arcgroup;
		Arcgroup processNode = new Arcgroup();
		Curve sbmlCurve;
		String clazz = "unspecified entity";
		
//		RenderGraphicalObjectPlugin renderGraphicalObjectPlugin = (RenderGraphicalObjectPlugin) generalGlyph.getPlugin(RenderConstants.shortLabel);
//		String objectRole = renderGraphicalObjectPlugin.getObjectRole();
//		clazz = mapObjectRoleToClazz(objectRole);
//		
//		if (clazz.equals("unspecified entity")){
			if (generalGlyph.getReferenceInstance() != null){
				clazz = sUtil.sbu.getOutputFromClass(generalGlyph.getReferenceInstance(), "unspecified entity");				
			}
//		}
		if (clazz.equals("unspecified entity")){
			try{
			clazz = sUtil.sbu.getOutputFromClass(generalGlyph, "unspecified entity");
			} catch(Exception e){}
		}
		

		if (generalGlyph.isSetCurve()){
			sbmlCurve = generalGlyph.getCurve();
			processNode = sUtil.createOneProcessNode(generalGlyph.getId(), sbmlCurve, clazz);
			//sOutput.addArcgroupToMap(processNode);	
			
			
			// reset the style to just a simple BBox
			sUtil.createBBox(generalGlyph, processNode.getGlyph().get(0));
		}
		if (generalGlyph.isSetBoundingBox()){
			glyph = createFromOneGraphicalObject(sbgnObject, generalGlyph);
			listOfGlyphs.add(glyph);
			processNode.getGlyph().add(glyph);
		}
		if (generalGlyph.isSetListOfReferenceGlyphs()){
			listOfReferenceGlyphs = generalGlyph.getListOfReferenceGlyphs();
			
			for (ReferenceGlyph referenceGlyph : listOfReferenceGlyphs) {
				arc = createFromOneReferenceGlyph(referenceGlyph);
				listOfArcs.add(arc);
				processNode.getArc().add(arc);
			}
		}
		if (generalGlyph.isSetListOfSubGlyphs()){
			listOfSubGlyphs = generalGlyph.getListOfSubGlyphs();
			
			for (GraphicalObject graphicalObject : listOfSubGlyphs) {
				glyph = createFromOneGraphicalObject(sbgnObject, graphicalObject);
				listOfGlyphs.add(glyph);
				processNode.getGlyph().add(glyph);
			}
		}	

		
		arcgroup = sUtil.createOneArcgroup(listOfGlyphs, listOfArcs, generalGlyph.getId());
		arcgroup.getGlyph().addAll(processNode.getGlyph());
		arcgroup.getArc().addAll(processNode.getArc());
		
		try {
			sUtil.addAnnotationInExtension(arcgroup, generalGlyph.getAnnotation());
			if (generalGlyph.getReferenceInstance() != null){
				sUtil.addAnnotationInExtension(arcgroup, generalGlyph.getReferenceInstance().getAnnotation());
			}
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return arcgroup;
	}
	
	public Arc createFromOneReferenceGlyph(ReferenceGlyph referenceGlyph){
		Arc arc;
		Curve sbmlCurve;
		
		String referenceGlyphId;
		String glyph;
		String reference;
		String role;
		
		referenceGlyphId = referenceGlyph.getId();
		glyph = referenceGlyph.getGlyph();
		reference = referenceGlyph.getReference();
		role = referenceGlyph.getRole();
		
		sUtil.printHelper("createGlyphsFromGeneralGlyphs", 
				String.format("id=%s, glyph=%s, reference=%s, role=%s \n", 
				referenceGlyphId, glyph, reference, role));
		
		sbmlCurve = referenceGlyph.getCurve();
		// todo: this is wrong, need to create one arc for each referenceGlyph, not for each curveSegment
		// todo: need source/target glyphs without violating syntax? i.e process nodes always connect arcs?
		// can't do need bezier, libSBGN doesn't have it
		// need port
		arc = sUtil.createOneArc(sbmlCurve);
		
		RenderGraphicalObjectPlugin renderGraphicalObjectPlugin = (RenderGraphicalObjectPlugin) referenceGlyph.getPlugin(RenderConstants.shortLabel);
		String objectRole = renderGraphicalObjectPlugin.getObjectRole();
		String clazz = mapObjectRoleToClazz(objectRole);
		
		// todo: need to determine from getOutputFromClass between production/consumption etc
		if (clazz.equals("unspecified entity")){
			if (role != null){
				clazz = sUtil.searchForReactionRole(role);
			}
		}
		if (clazz == null){
			clazz = sUtil.sbu.getOutputFromClass(referenceGlyph, "unknown influence");
		} 
//		if (clazz.equals("unknown influence")){
//			clazz = sUtil.sbu.getOutputFromClass(referenceGlyph.getReferenceInstance(), "unknown influence");
//		}
		arc.setClazz(clazz);
		
		// todo: confirm clazz with getOutputFromClass
					
		try {
			sUtil.addAnnotationInExtension(arc, referenceGlyph.getAnnotation());
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		addPortForArc(referenceGlyph, arc);
			
		return arc;
	}
	
	public void addPortForArc(GraphicalObject referenceGlyph, Arc arc){
		String sourceId = null;
		String targetId = null;
		List<CVTerm> cvTerms = referenceGlyph.getAnnotation().getListOfCVTerms();
		boolean hasPort = false;
		for (CVTerm cvt : cvTerms){
			if (cvt.getBiologicalQualifierType().getElementNameEquivalent().equals(Qualifier.BQB_HAS_PROPERTY.getElementNameEquivalent())){
				sourceId = cvt.getResources().get(0);
				targetId = cvt.getResources().get(1);
				hasPort = true;
			}
		}
		
		if (hasPort){
			SWrapperGlyphEntityPool sWrapperGlyph = sWrapperMap.listOfSWrapperGlyphEntityPools.get(sourceId);
			if (sWrapperGlyph == null){return;}
			System.out.println("]]]]]]]]]createFromOneReferenceGlyph " + sWrapperGlyph.id);
			
			Boolean addPort = false;
			if (sWrapperGlyph.clazz.equals("and")) {
				addPort =  true;
			} else if (sWrapperGlyph.clazz.equals("or")) {
				addPort =  true;
			} else if (sWrapperGlyph.clazz.equals("not")) {
				addPort =  true;
			}
			
			if (addPort){
				Bbox bbox = sWrapperGlyph.glyph.getBbox();
				Port port = new Port();
				port.setId("Port" + "__" + sourceId + "_" + targetId);
				port.setX(arc.getStart().getX());
				port.setY(arc.getStart().getY());
				sWrapperGlyph.glyph.getPort().add(port);
				arc.setSource(port);
				
				System.out.println("===]]createFromOneReferenceGlyph " + sWrapperGlyph.id);
				arc.setTarget(null);
				
			}
			
			sWrapperGlyph = sWrapperMap.listOfSWrapperGlyphEntityPools.get(targetId);
			Glyph targetGlyph;
			SWrapperGlyphProcess sWrapperReaction;
			if (sWrapperGlyph == null){
				sWrapperReaction = sWrapperMap.listOfSWrapperGlyphProcesses.get(targetId);
				targetGlyph = sWrapperReaction.processNodeGlyph;
			} else {
				targetGlyph = sWrapperGlyph.glyph;
			}
			
			
			if (targetGlyph == null){return;}
			System.out.println("[[[[[[[[createFromOneReferenceGlyph " + targetGlyph.getId());
			
			String targetClazz = targetGlyph.getClazz();
			
			addPort = false;
			if (targetClazz.equals("and")) {
				addPort =  true;
			} else if (targetClazz.equals("or")) {
				addPort =  true;
			} else if (targetClazz.equals("not")) {
				addPort =  true;
			}
			
			if (addPort){
				Bbox bbox = sWrapperGlyph.glyph.getBbox();
				Port port = new Port();
				port.setId("Port" + "__" + sourceId + "_" + targetId);
				port.setX(arc.getEnd().getX());
				port.setY(arc.getEnd().getY());
				targetGlyph.getPort().add(port);
				arc.setTarget(port);
			
				System.out.println("===]]createFromOneReferenceGlyph " + sWrapperGlyph.id);
				arc.setSource(null);
			}
		}		
	}
	
	public Glyph createFromOneGraphicalObject(Sbgn sbgnObject, GraphicalObject graphicalObject){
		Glyph sbgnGlyph;
		
		SBase sbase = sOutput.sbmlModel.getSBaseById(graphicalObject.getMetaidRef());
		if (sbase == null){
			sbase = graphicalObject;
		}
		String clazz = "unspecified entity";
		try{
		clazz = sUtil.sbu.getOutputFromClass(sbase, "unspecified entity");
		} catch(Exception e){}
		if (clazz.equals("unspecified entity")){
			try{
			clazz = sUtil.sbu.getOutputFromClass(graphicalObject, "unspecified entity");
			} catch(Exception e){}
		}
		
		// create a new Glyph, set its Bbox, don't set a Label
		// todo: or clazz could be simple chemical etc.
		sbgnGlyph = sUtil.createGlyph(graphicalObject.getId(), clazz, 
				true, graphicalObject, 
				false, null);	
		// todo: set a Label
		
		// todo: create Auxiliary items?
		
		try {
			sUtil.addAnnotationInExtension(sbgnGlyph, graphicalObject.getAnnotation());
			sUtil.addAnnotationInExtension(sbgnGlyph, sbase.getAnnotation());
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("===>createFromOneGraphicalObject " + graphicalObject.getId());
		
		return sbgnGlyph;
	}	
	
	public static void main(String[] args) throws FileNotFoundException, SAXException, IOException {
		
		
		String sbmlFileNameInput;
		String sbgnFileNameOutput;
		SBMLDocument sbmlDocument;
		SBML2SBGNML_GSOC2017 sbml2sbgnml;
		Sbgn sbgnObject;
		File file;
		
		if (args.length < 1 || args.length > 3) {
			System.out.println("usage: java org.sbfc.converter.sbml2sbgnml.SBML2SBGNML_GSOC2017 <SBML filename>. "
					+ "An example of relative path: /examples/sbml_layout_examples/GeneralGlyph_Example.xml");
		}

		// "/test-examples/sbml_layout_unittest/UnitTest_SBML_layout-L3V1.xml"
		
		String workingDirectory = System.getProperty("user.dir");

		sbmlFileNameInput = args[0];
		sbmlFileNameInput = workingDirectory + sbmlFileNameInput;	
		sbgnFileNameOutput = sbmlFileNameInput.replaceAll(".xml", "_SBGN-ML.sbgn");
		
		
		sbmlDocument = SBML2SBGNMLUtil.getSBMLDocument(sbmlFileNameInput);
		if (sbmlDocument == null) {
			throw new FileNotFoundException("The SBMLDocument is null");
		}
			
		sbml2sbgnml = new SBML2SBGNML_GSOC2017(sbmlDocument);
		// visualize JTree
//		try {		
//			sbml2sbgnml.sUtil.visualizeJTree(sbmlDocument);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}		
		
		sbgnObject = sbml2sbgnml.convertToSBGNML(sbmlDocument);	
		
		file = new File(sbgnFileNameOutput);
		try {
			SbgnUtil.writeToFile(sbgnObject, file);
		} catch (JAXBException e) {
			e.printStackTrace();
		}
		
		System.out.println("sbml2sbgnml.SBML2SBGNML_GSOC2017.main " + "output file at: " + sbgnFileNameOutput);

	}	

	@Override
	public GeneralModel convert(GeneralModel model) throws ConversionException, ReadModelException {

		try {
			inputModel = model;
			SBMLDocument sbmlDoc = ((SBMLModel) model).getSBMLDocument();
			Sbgn sbgnObj = convertToSBGNML(sbmlDoc);
			SBGNModel outputModel = new SBGNModel(sbgnObj);
			return outputModel;
		} catch (SBMLException e) {
			e.printStackTrace();
			throw new ConversionException(e.getMessage());
		}
	}

	@Override
	public String getResultExtension() {
		return ".sbgn";
	}
	
	@Override
	public String getName() {
		return "SBML2SBGNML";
	}
	
	@Override
	public String getDescription() {
		return "It converts a model format from SBML to SBGN-ML";
	}

	@Override
	public String getHtmlDescription() {
		return "It converts a model format from SBML to SBGN-ML";
	}
}
