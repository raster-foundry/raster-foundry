Templates:
List of immutable analysis references.
Latest -> virtual reference to the latest version
Analysis node can point to a template version or the last in list of versions (block box concept for later use, not supported yet)

Publishing a template creates a new, immutable analysis which is referenced in the template

Analysis contain a single AST
Workspaces contain a list of analyses. Have notion of an active analysis
New workflow centers around workspaces, which can contain multiple analyses
Creating a new node either creates a new analysis or attaches it to a existing analysis.


New model structure

Workspaces
* User point of entry for creating analysis workflows
* User searches by workspace, not by analysis
* Contain multiple analyses
* Allow specifying a default analysis for rendering / sharing
* Import and export templates
* Compare outputs of analyses
* Migration should create a new workspace for every existing analysis
* Properties
  > UUID id  
  > String name  
  > list[]  

Analyses
* Individual AST's
* Modified in place, changes effective immediately
* Not required to be named anymore, but can be linked to a template for easier exports
* In the future, will be able to reference templates or other analyses using a ToolReferenceNode (probably renamed)
* Backend model and api for analyses should not need to change

Templates
* List of immutable AST exports with metadata
* Properties:

Models:
* Templates
  > UUID id  
  > String Name  
  > String Details  
  > String Description  
  > String thumbnailUrl  
  > UUID[User] owner  
  > UUID[User] createdBy  
  > UUID[User] modifiedBy  
  > Date createdAt  
  > Date modifiedAt  
  > UUID Organization  
  > Visiblity visiblity  
  > String Requirements  
  > String license  
  > UUID[TemplateCategory] categories  
  > UUID[TemplateTag] tags  
  > Array[TemplateVersion] versions  
  
* TemplateCategory
  renamed from ToolCategory
  
* TemplateTag
  Renamed from ToolTag

* TemplateVersion
  > UUID: id  
  > String label  
  > UUID[Analysis] analysis  
  > Date createdAt  
  > Date modifiedAt  
  > UUID[User] createdBy  
  > UUID[User] modifiedBy  
