/*
 * Copyright 2026 JTaccuino Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jtaccuino.core.ui.documentation;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import jfx.incubator.scene.control.richtext.RichTextArea;
import jfx.incubator.scene.control.richtext.model.SimpleViewOnlyStyledModel;
import jfx.incubator.scene.control.richtext.model.StyledTextModel;

public class JavadocPopupSkin implements Skin<JavadocPopup> {

    private static final Logger LOGGER = Logger.getLogger(JavadocPopupSkin.class.getName());

    private final JavadocPopup control;
    private final RichTextArea content;

    @SuppressWarnings("this-escape")
    public JavadocPopupSkin(JavadocPopup control) {
        this.control = control;
        this.content = new RichTextArea(emptyModel());
        this.content.setEditable(false);
        this.content.setFocusTraversable(false);
        this.content.setWrapText(true);
        this.content.setPrefWidth(600);
        this.content.setMaxWidth(600);
        this.content.getStyleClass().add("javadoc-popup-content");
        setJavadoc(control.getJavadoc());
        control.javadocProperty().addListener((ov, oldValue, newValue) -> setJavadoc(newValue));
        control.typeNameProperty().addListener((ov, oldValue, newValue) -> setJavadoc(control.getJavadoc()));
        control.signatureProperty().addListener((ov, oldValue, newValue) -> setJavadoc(control.getJavadoc()));
    }

    private static StyledTextModel emptyModel() {
        try {
            return SimpleViewOnlyStyledModel.of("");
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create empty javadoc model", ex);
            return new SimpleViewOnlyStyledModel();
        }
    }

    private void setJavadoc(String javadoc) {
        try {
            this.content.setModel(JavadocRenderer.render(javadoc, control.getTypeName(), control.getSignature()));
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to create javadoc model", ex);
        }
    }

    @Override
    public JavadocPopup getSkinnable() {
        return control;
    }

    @Override
    public Node getNode() {
        return content;
    }

    @Override
    public void dispose() {
    }
}
