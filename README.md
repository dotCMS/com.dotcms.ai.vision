### Merged into the core as of Sept 12, 2025





# OpenAI Vision/Translation Plugin

## Overview

The OpenAI Vision and Translation Plugin is designed to use OpenAI's newer models to automatically tag images, add alt text descriptions and translate content in your DotCMS instance. This plugin leverages OpenAI's vision and json_format capabilities to enhance the localization, accessibility and searchability of your content.

## Features

- **Auto-Tagging**: Automatically tags images based on their content.
- **Alt Text Generation**: Generates and adds alt text descriptions to images.
- **Content Translation**: Integrates with DotCMS workflows to translate content, including WYSIWYG and Block Editor content into multiple languages.  Supports a  lookup table for industry specific term translations.

## Image Auto-Tagging and Descriptions

1. **Set up OpenAI API Key**:
    - Navigate to the DotCMS admin panel.
    - Go to `Apps` Screen and 
    - Add your OpenAI API key under the `dotAI` settings.
    - **Important:** Make sure you set dotAI to use a model that supports AI vision (**gpt-4o***, etc).

2. **Configure Auto Tagging**:
    - dotAI Auto Tagging looks for two field variables, `dotAITagSrc` and `dotAIDescriptionSrc`.  These field variables determine which fields should be auto-tag/auto-alt and which image should be read as the source of those values.  The value of the variables is the field you want to use as the source image to be read.
    - Edit the content type you want to auto-tag or auto alt text.
    - Auto-tagging: add a field variable property called `dotAITagSrc` on a tag field with the variable name of the image field that should be tagged.  For example, in a `dotAsset` content type, you would a field variable property to the `tags` field called `dotAITagSrc` with a value of `asset`.
    - Auto-Alt-Text: add a field variable property called `dotAIDescriptionSrc` that points to the variable name of the image field that should be read for alt text.  For example, in a `dotAsset` content type, you can add a description text field called `altText` and would add a field variable property to it `dotAIDescriptionSrc` with a value of `asset`.

### Tagging Content on Publish
The plugin provides a ContentListener that will auto-tag/auto-alt-text images when content of a type that has configured fields is published.  

### Tagging Actionlet
The plugin also provides a workflow action that will auto-tag content as well if the content is of a type that has been configured. You can add it to the workflow action you want to trigger the auto-tagging/auto-alt-text in. Please be sure to add it before the saving/publishing step in your workflow action.

1. **Add the Actionlet to a Workflow**:
    - Go to `Workflow` portlet.
    - Edit the desired workflow and add the `Open AI Auto-Tag Images` actionlet to the appropriate steps.
### Configs

- `AI_VISION_MODEL` - The model to use for AI vision - defaults to `gpt-4o`
- `AI_VISION_MAX_TOKENS` - The maximum number of tokens to generate for the alt text - defaults to `500`


## OpenAI Translations
The plugin also provides a workflow actionlet that can use OpenAI to do translations.  You can add this actionlet to any workflow and fire it (hopefully async, as it can take a while to complete).

By default, the actionlet will translate your `text`, `wysiwyg`, `textarea` and `storyblock` fields.  You can specify which types of fields to include when trying to translate the content. The prompt is constructed to try to prevent openAI's response from corrupting any `HTML` or `JSON` based tags found in `WYSIWYG` and `StoryBlock` fields.

You can configure in the workflow action which field types you would like to auto-translate, plus which other fields (by field var) you want to always include, minus any fields you never want to auto-translate.

### Lookup table
You can also specify which language properties you want to include to be used as a lookup table by Open AI when doing the translation.   This is useful when doing domain specific translations that expect industry specific terms to be translated in an exact/non-standard way. You can specify a language key prefix to use to load the language property variables for the lookup table, e.g. `translation.context.` and only variables whose key starts with that prefix, e.g. `translation.context.cms` and `translation.context.content.management` will be included in the lookup table.

### Configs
- AI_TRANSLATION_SYSTEM_PROMPT =  set in the plugin.properties file
- AI_TRANSLATION_USER_PROMPT = set in the plugin.properties file
- AI_TRANSLATION_MODEL_KEY = gpt-4o;
- AI_TRANSLATIONS_MAX_TOKENS = // not set;
- AI_TRANSLATION_TEMPERATURE = .01f
- AI_TRANSLATION_RESPONSE_FORMAT = "json_format" // uses the new json response format.

## Requirements

- Java
- Maven
- DotCMS
- OpenAI API Key

## Installation

1. **Clone the repository**:
    ```sh
    git clone https://github.com/dotCMS/com.dotcms.ai.v2.git
    cd com.dotcms.ai.v2
    ```

2. **Build the project**:
    ```sh
    ./mnnw clean install
    ```

3. **Deploy the plugin**:
   - Upload the generated JAR file from the `target` directory to your dotCMS plugins.


## Development

### Prerequisites

- Java 11+
- Maven

### Building the Project

1. **Open the project in IntelliJ IDEA**.
2. **Build the project** using Maven:
    ```sh
    mvn clean install
    ```
