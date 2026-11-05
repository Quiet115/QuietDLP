package com.quietdlp.app.data

/**
 * Las claves que algunas webs de fotos piden además de la sesión (usuario numérico y clave de API de la cuenta del propio
 * usuario). Funciones puras, para poder probarlas.
 */
object SiteKeys {
    private val CATEGORY = Regex("""^[a-z0-9-]{1,30}$""")

    /**
     * El nombre que gallery-dl da a una web, sacado de la dirección que escribe el usuario: "www.ejemplo.com" y
     * "https://ejemplo.com/x" dan "ejemplo". null si no parece una dirección.
     */
    fun category(address: String): String? =
        SiteRules.normalizeHost(address)?.substringBefore('.')?.takeIf { CATEGORY.matches(it) }

    private val USER = Regex("""^\d{1,12}$""")
    private val KEY = Regex("""^[A-Za-z0-9]{16,200}$""")

    fun cleanUser(input: String): String = input.trim().filter { it.isDigit() }.take(12)

    fun cleanKey(input: String): String = input.trim().filter { it.isLetterOrDigit() }.take(200)

    fun valid(user: String, key: String): Boolean = USER.matches(user) && KEY.matches(key)

    /**
     * Busca el usuario y la clave en el texto de la página de la cuenta. La página los enseña como
     * "&api_key=...&user_id=..." y, según la versión, también en campos aparte; se prueban varias formas.
     */
    fun parse(text: String): Pair<String, String>? {
        val key = listOf(
            Regex("""api_key=([A-Za-z0-9]{16,200})"""),
            Regex("""api[ _-]?key\W{1,12}([A-Za-z0-9]{32,200})""", RegexOption.IGNORE_CASE),
        ).firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
        val user = listOf(
            Regex("""user_id=(\d{1,12})"""),
            Regex("""user[ _-]?id\W{1,12}(\d{1,12})""", RegexOption.IGNORE_CASE),
        ).firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
        if (user == null || key == null || !valid(user, key)) return null
        return user to key
    }

    /** Lo que se le pasa a gallery-dl (entre comillas: si no, una clave que parezca un número se leería como número). */
    fun galleryArgs(address: String, user: String, key: String): List<String> {
        val site = category(address)
        return if (site == null || !valid(user, key)) emptyList()
        else listOf("-o", "extractor.$site.user-id=\"$user\"", "-o", "extractor.$site.api-key=\"$key\"")
    }
}
