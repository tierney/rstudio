/*
 * FindOutputPane.java
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
package org.rstudio.studio.client.workbench.views.output.find;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import org.rstudio.core.client.CodeNavigationTarget;
import org.rstudio.core.client.dom.DomUtils;
import org.rstudio.core.client.events.EnsureVisibleEvent;
import org.rstudio.core.client.events.HasSelectionCommitHandlers;
import org.rstudio.core.client.events.SelectionCommitEvent;
import org.rstudio.core.client.events.SelectionCommitHandler;
import org.rstudio.core.client.widget.*;
import org.rstudio.core.client.widget.events.SelectionChangedHandler;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.ui.WorkbenchPane;
import org.rstudio.studio.client.workbench.views.output.find.model.FindResult;

import java.util.ArrayList;


public class FindOutputPane extends WorkbenchPane
      implements FindOutputPresenter.Display,
                 HasSelectionCommitHandlers<CodeNavigationTarget>
{
   @Inject
   public FindOutputPane(Commands commands)
   {
      super("Find Results");
      commands_ = commands;
      ensureWidget();
   }

   @Override
   protected Toolbar createMainToolbar()
   {
      Toolbar toolbar = new Toolbar();

      searchLabel_ = new Label();
      toolbar.addLeftWidget(searchLabel_);

      stopSearch_ = new ToolbarButton(
            commands_.interruptR().getImageResource(),
            (ClickHandler) null);
      stopSearch_.setVisible(false);

      toolbar.addRightWidget(stopSearch_);


      return toolbar;
   }

   @Override
   protected Widget createMainWidget()
   {
      context_ = new FindResultContext();

      FindOutputResources resources = GWT.create(FindOutputResources.class);
      resources.styles().ensureInjected();

      table_ = new FastSelectTable<FindResult, CodeNavigationTarget, Object>(
            new FindOutputCodec(resources),
            resources.styles().selectedRow(),
            true,
            false);
      FontSizer.applyNormalFontSize(table_);
      table_.addStyleName(resources.styles().findOutput());
      table_.addClickHandler(new ClickHandler()
      {
         @Override
         public void onClick(ClickEvent event)
         {
            if (event.getNativeButton() != NativeEvent.BUTTON_LEFT)
               return;

            if (dblClick_.checkForDoubleClick(event.getNativeEvent()))
               fireSelectionCommitted();
         }

         private final DoubleClickState dblClick_ = new DoubleClickState();
      });

      table_.addKeyDownHandler(new KeyDownHandler()
      {
         @Override
         public void onKeyDown(KeyDownEvent event)
         {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER)
               fireSelectionCommitted();
            event.stopPropagation();
            event.preventDefault();
         }
      });

      scrollPanel_ = new ScrollPanel(table_);
      scrollPanel_.setSize("100%", "100%");
      return scrollPanel_;
   }

   private void fireSelectionCommitted()
   {
      ArrayList<CodeNavigationTarget> values = table_.getSelectedValues();
      if (values.size() == 1)
         SelectionCommitEvent.fire(this, values.get(0));
   }

   @Override
   public void addMatches(ArrayList<FindResult> findResults)
   {
      int matchesToAdd = Math.min(findResults.size(), MAX_COUNT - matchCount_);

      if (matchesToAdd > 0)
      {
         matchCount_ += matchesToAdd;
         table_.addItems(findResults.subList(0, matchesToAdd), false);
      }

      if (matchesToAdd != findResults.size())
         showOverflow();
   }

   @Override
   public void clearMatches()
   {
      context_.reset();
      table_.clear();
      overflow_ = false;
      matchCount_ = 0;
   }

   @Override
   public void ensureVisible(boolean activate)
   {
      fireEvent(new EnsureVisibleEvent(activate));
   }

   @Override
   public HasClickHandlers getStopSearchButton()
   {
      return stopSearch_;
   }

   @Override
   public void setStopSearchButtonVisible(boolean visible)
   {
      stopSearch_.setVisible(visible);
   }

   @Override
   public void ensureSelectedRowIsVisible()
   {
      ArrayList<TableRowElement> rows = table_.getSelectedRows();
      if (rows.size() > 0)
      {
         DomUtils.ensureVisibleVert(scrollPanel_.getElement(),
                                    rows.get(0),
                                    20);
      }
   }

   @Override
   public HandlerRegistration addSelectionChangedHandler(SelectionChangedHandler handler)
   {
      return table_.addSelectionChangedHandler(handler);
   }

   @Override
   public void showOverflow()
   {
      if (overflow_)
         return;
      overflow_ = true;
      ArrayList<FindResult> items = new ArrayList<FindResult>();
      items.add(null);
      table_.addItems(items, false);
   }

   @Override
   public void updateSearchLabel(String query, String path)
   {
      SafeHtmlBuilder builder = new SafeHtmlBuilder();
      builder.appendEscaped("Results for ")
            .appendHtmlConstant("<strong>")
            .appendEscaped(query)
            .appendHtmlConstant("</strong>")
            .appendEscaped(" in ")
            .appendEscaped(path);
      searchLabel_.getElement().setInnerHTML(builder.toSafeHtml().asString());
   }

   @Override
   public void clearSearchLabel()
   {
      searchLabel_.setText("");
   }

   @Override
   public HandlerRegistration addSelectionCommitHandler(SelectionCommitHandler<CodeNavigationTarget> handler)
   {
      return addHandler(handler, SelectionCommitEvent.getType());
   }

   private FastSelectTable<FindResult, CodeNavigationTarget, Object> table_;
   private FindResultContext context_;
   private final Commands commands_;
   private Label searchLabel_;
   private ToolbarButton stopSearch_;
   private ScrollPanel scrollPanel_;
   private boolean overflow_ = false;
   private int matchCount_;

   // This must be the same as MAX_COUNT in SessionFind.cpp
   private static final int MAX_COUNT = 1000;
}
