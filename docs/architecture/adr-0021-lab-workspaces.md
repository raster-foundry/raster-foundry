### Design notes
Templates:
List of immutable analysis references.
Latest -> virtual reference to the latest version
Analysis node can point to a template version or the last in list of versions (block box concept for later use, not supported yet)

Publishing a template creates a new, immutable analysis which is referenced in the template

Analysis contain a single AST
Workspaces contain a list of analyses. Have notion of an active analysis
New workflow centers around workspaces, which can contain multiple analyses
Creating a new node either creates a new analysis or attaches it to a existing analysis.


## Workspaces
* User point of entry for creating analysis workflows
* User searches by workspace, not by analysis
* Contain multiple analyses
* Allow specifying a default analysis for rendering / sharing
* Import and export templates
* Compare outputs of analyses
* Migration should create a new workspace for every existing analysis

## Analyses
* Individual AST's
* Modified in place, changes effective immediately
* Not required to be named anymore, but can be linked to a template for easier exports
* In the future, will be able to reference templates or other analyses using a ToolReferenceNode (probably renamed)
* Add readonly attrubute to prevent exported analyses from being edited

## Templates
* List of immutable AST exports with metadata
* Properties:

### Models:
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
  > Array[TemplateVersion] versions: needs to be ordered, and only needs to be one way with no searching  

* TemplateCategory
  > Template
  > Category
  
* TemplateTag
  > Template
  > Tag

* TemplateVersion
  > UUID: id  
  > String label  
  > UUID[Analysis] analysis  
  > Date createdAt  
  > Date modifiedAt  
  > UUID[User] createdBy  
  > UUID[User] modifiedBy  

* Workspace
  > UUID id  
  > String name  
  > String description  
  > UUID[Analysis] activeAnalysis  
  
* WorkspaceCategory
  > workspace  
  > category  

* WorkspaceTag
  > workspace  
  > tag  

* WorkspaceAnalysis
  > workspace  
  > analysis  
  > createdAt  
  > modifiedAt  
  > createdBy  
  > modifiedBy  
