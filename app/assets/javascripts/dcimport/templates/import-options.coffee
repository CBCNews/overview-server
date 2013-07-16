define ['underscore'], (_) ->
  template = _.template("""
    <div id="import-options">
      <form method="post" class="form-inline update" action="#split-document">
        <input id="set-split-documents" type="checkbox" name="split-documents" value="false" />
        <label for="set-split-documents"><%- i18n('views.DocumentSet._dcimport.labels.split_documents') %></label>
      </form>
      <form method="post" class="form-inline update" action="#supported-language">
        <label for="set-lang"><%- i18n('views.DocumentSet._dcimport.labels.language') %></label>
        <select name="lang" id="set-lang">
          <% _.each(window.supportedLanguages, function(lang) { %>
            <option
              <%- lang.code == window.defaultLanguageCode && 'selected="selected"' || '' %>
              value="<%- lang.code %>"
              ><%- lang.name %></option>
          <% }) %>
        </select>
      </form>
    </div>""")
