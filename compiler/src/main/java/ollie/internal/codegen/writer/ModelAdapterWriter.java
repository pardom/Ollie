/*
 * Copyright (C) 2014 Michael Pardo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ollie.internal.codegen.writer;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.squareup.javawriter.JavaWriter;
import ollie.Model;
import ollie.annotation.Table;
import ollie.internal.ModelAdapter;
import ollie.internal.codegen.Registry;
import ollie.internal.codegen.element.ColumnElement;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;

public class ModelAdapterWriter implements SourceWriter<TypeElement> {
	private static final Map<String, String> CURSOR_METHOD_MAP = new HashMap<String, String>() {
		{
			put(byte[].class.getName(), "getBlob");
			put(Byte[].class.getName(), "getBlob");
			put(double.class.getName(), "getDouble");
			put(Double.class.getName(), "getDouble");
			put(float.class.getName(), "getFloat");
			put(Float.class.getName(), "getFloat");
			put(int.class.getName(), "getInt");
			put(Integer.class.getName(), "getInt");
			put(long.class.getName(), "getLong");
			put(Long.class.getName(), "getLong");
			put(short.class.getName(), "getShort");
			put(Short.class.getName(), "getShort");
			put(String.class.getName(), "getString");
		}
	};

	private static final Set<Modifier> MODIFIERS = EnumSet.of(PUBLIC, FINAL);

	private Registry registry;

	public ModelAdapterWriter(Registry registry) {
		this.registry = registry;
	}

	@Override
	public String createSourceName(TypeElement element) {
		return "ollie." + createSimpleName(element);
	}

	@Override
	public void writeSource(Writer writer, TypeElement element) throws IOException {
		final String simpleName = createSimpleName(element);
		final String modelSimpleName = element.getSimpleName().toString();
		final String modelQualifiedName = element.getQualifiedName().toString();
		final String tableName = element.getAnnotation(Table.class).value();
		final Set<ColumnElement> columns = registry.getColumnElements(element);

		JavaWriter javaWriter = new JavaWriter(writer);
		javaWriter.setCompressingTypes(true);
		javaWriter.setIndent("\t");

		javaWriter.emitSingleLineComment("Generated by Ollie. Do not modify!");
		javaWriter.emitPackage("ollie");

		writeImports(javaWriter, modelQualifiedName, columns);

		javaWriter.beginType(simpleName, "class", MODIFIERS, "ModelAdapter<" + modelSimpleName + ">");

		writeGetModelType(javaWriter, modelSimpleName);
		writeGetTableName(javaWriter, tableName);
		writeGetSchema(javaWriter, tableName, columns);
		writeLoad(javaWriter, modelQualifiedName, columns);
		writeSave(javaWriter, modelQualifiedName, columns);
		writeDelete(javaWriter, modelQualifiedName, tableName);

		javaWriter.endType();
	}

	private void writeImports(JavaWriter writer, String modelQualifiedName, Set<ColumnElement> columns)
			throws IOException {

		Set<String> imports = Sets.newHashSet(
				modelQualifiedName,
				ContentValues.class.getName(),
				Cursor.class.getName(),
				SQLiteDatabase.class.getName(),
				ModelAdapter.class.getName()
		);

		for (ColumnElement column : columns) {
			if (column.isModel()) {
				imports.add(Long.class.getName());
			}
			if (column.requiresTypeAdapter()) {
				imports.add(column.getDeserializedQualifiedName());
				imports.add(column.getSerializedQualifiedName());
			}
		}

		writer.emitImports(imports);
	}

	private void writeGetModelType(JavaWriter writer, String modelSimpleName) throws IOException {
		writer.beginMethod("Class<? extends Model>", "getModelType", MODIFIERS);
		writer.emitStatement("return " + modelSimpleName + ".class");
		writer.endMethod();
		writer.emitEmptyLine();
	}

	private void writeGetTableName(JavaWriter writer, String tableName) throws IOException {
		writer.beginMethod("String", "getTableName", MODIFIERS);
		writer.emitStatement("return \"" + tableName + "\"");
		writer.endMethod();
		writer.emitEmptyLine();
	}

	private void writeGetSchema(JavaWriter writer, String tableName, Set<ColumnElement> columns) throws IOException {
		writer.beginMethod("String", "getSchema", MODIFIERS);

		List<String> definitions = new ArrayList<String>();
		for (ColumnElement column : columns) {
			String schema = column.getSchema();
			if (!Strings.isNullOrEmpty(schema)) {
				definitions.add(schema);
			}
		}
		for (ColumnElement column : columns) {
			String foreignKeyClause = column.getForeignKeyClause();
			if (!Strings.isNullOrEmpty(foreignKeyClause)) {
				definitions.add(foreignKeyClause);
			}
		}

		writer.emitStatement("return \"CREATE TABLE IF NOT EXISTS %s (%s)\"",
				tableName,
				Joiner.on(", ").join(definitions));

		writer.endMethod();
		writer.emitEmptyLine();
	}

	private void writeLoad(JavaWriter writer, String modelQualifiedName, Set<ColumnElement> columns) throws
			IOException {

		writer.beginMethod("void", "load", MODIFIERS, modelQualifiedName, "entity", "Cursor", "cursor");

		for (ColumnElement column : columns) {
			final StringBuilder value = new StringBuilder();

			int closeParens = 1;
			if (column.isModel()) {
				closeParens++;
				value.append("Ollie.getOrFindEntity(entity.");
				if ( column.getAccessorMethod() != null ) {
					value.append(column.getAccessorMethod().getSimpleName().toString()).append("()");
				} else {
					value.append(column.getFieldName());
				}
				value.append(".getClass(), ");
			} else if (column.requiresTypeAdapter()) {
				closeParens++;
				value.append("Ollie.getTypeAdapter(")
						.append(column.getDeserializedSimpleName())
						.append(".class).deserialize(");
			}

			value.append("cursor.").append(CURSOR_METHOD_MAP.get(column.getSerializedQualifiedName())).append("(");
			value.append("cursor.getColumnIndex(\"").append(column.getColumnName()).append("\")");

			for (int i = 0; i < closeParens; i++) {
				value.append(")");
			}

			if ( column.getMutatorMethod() != null ) {
				writer.emitStatement("entity." + column.getMutatorMethod().getSimpleName().toString() + "(" + value.toString() + ")");
			} else {
				writer.emitStatement("entity." + column.getFieldName() + " = " + value.toString());
			}
		}

		writer.endMethod();
		writer.emitEmptyLine();
	}

	private void writeSave(JavaWriter writer, String modelQualifiedName, Set<ColumnElement> columns) throws
			IOException {

		writer.beginMethod("Long", "save", MODIFIERS, modelQualifiedName, "entity", "SQLiteDatabase", "db");
		writer.emitStatement("ContentValues values = new ContentValues()");

		for (ColumnElement column : columns) {
			final StringBuilder value = new StringBuilder();
			int closeParens = 0;

			if (!column.isModel() && column.requiresTypeAdapter()) {
				closeParens++;
				value.append("(").append(column.getSerializedSimpleName())
						.append(") Ollie.getTypeAdapter(")
						.append(column.getDeserializedSimpleName())
						.append(".class).serialize(");
			}

			value.append("entity.");

			if ( column.getAccessorMethod() != null ) {
				value.append(column.getAccessorMethod().getSimpleName().toString()).append("()");
			} else {
				value.append(column.getFieldName());
			}

			if (column.isModel()) {
				value.append(" != null ? ");
				value.append("entity.");

				if ( column.getAccessorMethod() != null ) {
					value.append(column.getAccessorMethod().getSimpleName().toString()).append("()");
				} else {
					value.append(column.getFieldName());
				}
				value.append(".id");
				value.append(" : null");
			}

			for (int i = 0; i < closeParens; i++) {
				value.append(")");
			}

			writer.emitStatement("values.put(\"" + column.getColumnName() + "\", " + value.toString() + ")");
		}

		writer.emitStatement("return insertOrUpdate(entity, db, values)");
		writer.endMethod();
		writer.emitEmptyLine();
	}

	private void writeDelete(JavaWriter writer, String modelQualifiedName, String tableName) throws IOException {
		writer.beginMethod("void", "delete", MODIFIERS, modelQualifiedName, "entity", "SQLiteDatabase", "db");
		writer.emitStatement("db.delete(\"" + tableName + "\", \"" + Model._ID
				+ "=?\", new String[]{entity.id.toString()})");
		writer.endMethod();
		writer.emitEmptyLine();
	}

	private String createSimpleName(TypeElement element) {
		return element.getSimpleName().toString() + "$$ModelAdapter";
	}
}
