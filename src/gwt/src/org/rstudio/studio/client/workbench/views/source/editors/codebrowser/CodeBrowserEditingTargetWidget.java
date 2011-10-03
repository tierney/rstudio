/*
 * CodeBrowserEditingTargetWidget.java
 *
 * Copyright (C) 2009-11 by RStudio, Inc.
 *
 * This program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.workbench.views.source.editors.codebrowser;

import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.command.KeyboardShortcut;
import org.rstudio.core.client.widget.Toolbar;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.GlobalProgressDelayer;
import org.rstudio.studio.client.common.SimpleRequestCallback;
import org.rstudio.studio.client.common.codetools.CodeToolsServerOperations;
import org.rstudio.studio.client.common.filetypes.FileTypeRegistry;
import org.rstudio.studio.client.common.filetypes.TextFileType;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.server.Void;
import org.rstudio.studio.client.workbench.codesearch.model.SearchPathFunctionDefinition;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.views.console.shell.assist.CompletionManager;
import org.rstudio.studio.client.workbench.views.console.shell.editor.InputEditorLineWithCursorPosition;
import org.rstudio.studio.client.workbench.views.console.shell.editor.InputEditorUtil;
import org.rstudio.studio.client.workbench.views.source.PanelWithToolbar;
import org.rstudio.studio.client.workbench.views.source.editors.EditingTargetToolbar;
import org.rstudio.studio.client.workbench.views.source.editors.text.DocDisplay;
import org.rstudio.studio.client.workbench.views.source.events.CodeBrowserNavigationEvent;
import org.rstudio.studio.client.workbench.views.source.model.SourcePosition;

public class CodeBrowserEditingTargetWidget extends Composite
   implements CodeBrowserEditingTarget.Display
{
   public CodeBrowserEditingTargetWidget(Commands commands,
                                         final GlobalDisplay globalDisplay,
                                         final EventBus eventBus,
                                         final CodeToolsServerOperations server,
                                         final DocDisplay docDisplay)
   {
      commands_ = commands;
      
      docDisplay_ = docDisplay;
      
      panel_ = new PanelWithToolbar(createToolbar(),
                                    docDisplay_.asWidget());
      panel_.setSize("100%", "100%");
      
      docDisplay_.setReadOnly(true);
      
      // setup custom completion manager for executing F1 and F2 actions
      docDisplay_.setFileType(FileTypeRegistry.R, new CompletionManager() {

         @Override
         public boolean previewKeyDown(NativeEvent event)
         {
            int modifier = KeyboardShortcut.getModifierValue(event);
            if (modifier == KeyboardShortcut.NONE)
            {
               if (event.getKeyCode() == 112) // F1
               {
                  InputEditorLineWithCursorPosition linePos = 
                        InputEditorUtil.getLineWithCursorPosition(docDisplay);
              
                  server.getHelpAtCursor(
                     linePos.getLine(), linePos.getPosition(),
                     new SimpleRequestCallback<Void>("Help"));  
               }
               else if (event.getKeyCode() == 113) // F2
               {
                  goToFunctionDefinition();
               }
            }
            
            return false;
         }
         
         @Override
         public void goToFunctionDefinition()
         {
            // determine current line and cursor position
            InputEditorLineWithCursorPosition lineWithPos = 
                      InputEditorUtil.getLineWithCursorPosition(docDisplay);
             
            // delayed progress indicator
            final GlobalProgressDelayer progress = new GlobalProgressDelayer(
                  globalDisplay, 1000, "Searching for function definition...");
            
            server.findFunctionInSearchPath(
               lineWithPos.getLine(),
               lineWithPos.getPosition(), 
               currentFunctionNamespace_,
               new ServerRequestCallback<SearchPathFunctionDefinition>() {
                  @Override
                  public void onResponseReceived(SearchPathFunctionDefinition def)
                  {
                      // dismiss progress
                      progress.dismiss();
                          
                      // if we got a hit
                      if (def.getName() != null)
                      {         
                         // try to search for the function locally
                         SourcePosition position = 
                            docDisplay.findFunctionPositionFromCursor(
                                                      def.getName());
                         if (position != null)
                         {
                            docDisplay.navigateToPosition(position, true);
                         }
                         else if (def.getNamespace() != null)
                         {
                            eventBus.fireEvent(new CodeBrowserNavigationEvent(
                                                                         def));        
                         }
                     }
                  }

                  @Override
                  public void onError(ServerError error)
                  {
                     progress.dismiss();
                     
                     globalDisplay.showErrorMessage("Error Searching for Function",
                                                     error.getUserMessage());
                  }
               });
            
         }

         @Override
         public boolean previewKeyPress(char charCode)
         {
            return false;
         }
         
         @Override
         public void close()
         {
         }
         
      }); 
      
      initWidget(panel_);

   }
   
   @Override
   public Widget asWidget()
   {
      return this;
   }
   
   
   @Override
   public void adaptToFileType(TextFileType fileType)
   {
      docDisplay_.setFileType(fileType, true); 
   }


   @Override
   public void setFontSize(double size)
   {
      docDisplay_.setFontSize(size);
   }
   
   @Override
   public void onActivate()
   {
      docDisplay_.onActivate();
   }
   
   @Override
   public void showFunction(SearchPathFunctionDefinition functionDef)
   {
      currentFunctionNamespace_ = functionDef.getNamespace();
      docDisplay_.setCode(StringUtil.notNull(functionDef.getCode()), false);   
   }
   
   
   
   private Toolbar createToolbar()
   {
      Toolbar toolbar = new EditingTargetToolbar(commands_);
      toolbar.addLeftWidget(commands_.printSourceDoc().createToolbarButton());
      toolbar.addLeftSeparator();
      toolbar.addLeftWidget(commands_.goToFunctionDefinition().createToolbarButton());
  
      return toolbar;
   }



   private final PanelWithToolbar panel_;
   private final Commands commands_;
   private final DocDisplay docDisplay_;
   private String currentFunctionNamespace_ = null;
   
}