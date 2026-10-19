# Serena MCP Tool Rules

When using the Serena MCP tool `find_symbol`, you must follow this strict search protocol:
1. **Initial Search:** NEVER use the `relative_path` parameter on your first attempt. You must perform a broad workspace search using ONLY the `name_path_pattern` parameter.
2. **Refinement:** Only add the `relative_path` parameter as a fallback if the initial global search returns an overwhelmingly large or ambiguous list of results. 
3. **Java/LSP Quirks:** If a Serena tool returns an empty `[]` array, assume it might be an indexing or path-formatting issue and immediately broaden your search parameters before assuming the symbol does not exist.
