import org.w3c.dom.*
import org.w3c.dom.events.Event
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.File
import org.w3c.files.FilePropertyBag
import kotlin.browser.document
import kotlin.browser.window
import kotlin.dom.clear
import kotlin.dom.createElement
import kotlin.js.Json
import kotlin.js.Promise
import kotlin.js.json

// Բայց սոռտ է, մարդավարի ES5/ES6 ով կգրես, require('firebase/app') կամ import firebase from 'firebase'
// չի աշխատի, տողի վրա կգրես, կաշխատի 😂

var firebase: dynamic = js("firebase")
var dbRef = firebase.database().ref().child("projects")

fun main(args: Array<String>) {
    if (window.location.href.contains("index.html", false)) {

        window.onload = {
            val elems = document.querySelectorAll(".modal")
            val params = json("onCloseEnd" to fun () {
                (document.getElementById("project_name") as HTMLInputElement).value = ""
                (document.getElementById("project_alias") as HTMLInputElement).value = ""
            })
            js("M").Modal.init(elems, params)

            YandexHelper.supportedLanguages().then {
                val select = document.createElement("select") as HTMLSelectElement
                select.multiple = true
                var index = it.keys.indexOf("en")
                it.forEach {
                    val option = document.createElement("option") as HTMLOptionElement
                    option.value = it.key
                    option.text = it.value
                    select.appendChild(option)
                }
                select.options.selectedIndex = index
                val div = document.getElementById("languages-combobox") as HTMLDivElement
                div.insertBefore(select, div.firstChild)
                val elems = document.querySelectorAll("select")
                val instance = js("M").FormSelect.init(elems, {})
                val addButton = document.getElementById("add_project")
                addButton?.addEventListener("click", {
                    val projectName = (document.getElementById("project_name") as HTMLInputElement).value
                    val projectAlias = (document.getElementById("project_alias") as HTMLInputElement).value
                    val languages = arrayListOf<Pair<String, String>>()
                    for (i in 0..(select.selectedOptions.length - 1)) {
                        val option = select.selectedOptions[i] as HTMLOptionElement
                        languages.add(option.value to option.text)
                    }
                    if (projectAlias.isNotEmpty() && projectName.isNotEmpty() && languages.isNotEmpty()) {
                        createProject(projectName, projectAlias, languages.toTypedArray())
                    }
                })
            }
            getProjects {
                val divProjects = document.getElementById("row") as HTMLDivElement
                while (divProjects.childElementCount > 1) {
                    val lastChild = divProjects.lastChild
                    if (lastChild != null) {
                        divProjects.removeChild(lastChild)
                    }
                }
                // Projects
                it.forEach {
                    var projectCard = document.getElementById(it["alias"].toString())
                    if (projectCard == null) {
                        projectCard = document.createElement("div") as HTMLDivElement
                        projectCard.className = "col s12 m3"
                        projectCard.id = it["alias"].toString()
                        val card = document.createElement("div") as HTMLDivElement
                        card.className = "card"
                        card.setAttribute("data-alias", it["alias"].toString())
                        val cardContext = document.createElement("div") as HTMLDivElement
                        cardContext.className = "card-content black-text"
                        val cardTitle = document.createElement("span") as HTMLSpanElement
                        cardTitle.className = "card-title"
                        cardTitle.innerText = it["name"].toString()
                        val alias = document.createElement("p") as HTMLParagraphElement
                        alias.innerText = it["alias"].toString()
                        val platformContainer = document.createElement("div") as HTMLDivElement
                        platformContainer.className = "platform-container"
                        val iosImage = document.createElement("img") as HTMLImageElement
                        iosImage.src = "images/icon-ios.png"
                        iosImage.alt = "iOS"
                        iosImage.width = 24
                        iosImage.height = 24
                        val androidImage = document.createElement("img") as HTMLImageElement
                        androidImage.src = "images/icon-android.png"
                        androidImage.alt = "Android"
                        androidImage.width = 24
                        androidImage.height = 24
                        val webImage = document.createElement("img") as HTMLImageElement
                        webImage.src = "images/icon-website.png"
                        webImage.alt = "Web"
                        webImage.width = 24
                        webImage.height = 24
                        platformContainer.append(iosImage, androidImage, webImage)
                        cardContext.append(cardTitle, alias, platformContainer)
                        card.appendChild(cardContext)
                        projectCard.appendChild(card)
                        divProjects.appendChild(projectCard)
                    }

                    // remove loading
                    val loading = document.getElementById("indicator")
                    if (loading != null) {
                        divProjects.removeChild(loading as HTMLElement)
                    }
                }


                // Adding click event listener
                for (node: Node in divProjects.childNodes.asList()) {
                    node.addEventListener("click", fun(event: Event) {
                        val element = node as HTMLDivElement
                        val cardElement = element.firstElementChild
                        if (cardElement != null) {
                            val projectAlias = cardElement.getAttribute("data-alias")
                            if (projectAlias != "new-project") {
                                window.location.href = "./project.html?alias=${projectAlias}"
                            }
                        }
                    })
                }
            }
        }
    } else if (window.location.href.contains("project.html")) {
        val url = URL(document.location!!.href)
        val targetProjectAlias = url.searchParams.get("alias")

        if (targetProjectAlias != null) {
            val collectionElement = document.getElementById("collection-header")

            var projectJson: Json

            getProject(targetProjectAlias) {

                projectJson = it
                addLanguageInputsToPopup(it)

                val projectName = it["name"] as String
                val projectAlias = it["alias"] as String

                var languages = arrayListOf<String>()
                val languagesJson = it["languages"] as Json
                js("Object").values(languagesJson).forEach(fun (language: dynamic) {
                    languages.add(language["langName"] as String)
                })


                var screens = arrayOf<String>()
                val screensJson = it["screens"] as Json
                js("Object").values(screensJson).forEach(fun(screen: String) {
                    screens[screens.count()] = screen
                })

                initScreenAutocompleteList(screens)

                var types = arrayOf<String>()
                val typesJson = it["types"] as Json
                js("Object").values(typesJson).forEach(fun(type: String) {
                    types[types.count()] = type
                })

                initTypeAutocompleteList(types)

                if (collectionElement != null) {

                    var innerHtml =
                            "<div class=\"header-container\">" +
                                "<div class=\"header-container-base\">" +
                                    "<div>" +
                                        "<h5>${projectName}</h5>" +
                                        "<h6>${projectAlias}</h6>" +
                                     "</div>" +
                                    "<div class=\"export_button\">" +
                                        " <!-- Dropdown Trigger -->\n" +
                                    "  <a class='dropdown-trigger btn' href='#' data-target='dropdown1'>Export</a>\n" +
                                    "\n" +
                                    "  <!-- Dropdown Structure -->\n" +
                                    "  <ul id='dropdown1' class='dropdown-content'>\n" +
                                    "    <li><a href=\"#!\" id=\"export_ios\">iOS</a></li>\n" +
                                    "    <li class=\"divider\" tabindex=\"-1\"></li>\n" +
                                    "    <li><a href=\"#!\" id=\"export_android\">Andriod</a></li>\n" +
                                    "    <li class=\"divider\" tabindex=\"-1\"></li>\n" +
                                    "    <li><a href=\"#!\" id=\"export_web\">Web</a></li>\n" +
                                    "  </ul>" +
                                    "</div>" +
                                "</div>" +
                            "</div>"

                    val tableData =
                            "<table class=\"highlight centered responsive-table\">" +
                                    "<thead>" +
                                    "<tr>" +
                                    getColumNames(languages) +
                                    "</tr>" +
                                    "</thead>" +
                                    "<tbody>" +
                                    getRows(it) +
                                    "</tbody>" +
                                    "</table>"

                    innerHtml += tableData

                    innerHtml += "<div class=\"float_button\">" +
                                    "<a class=\"btn-floating waves-effect waves-light btn modal-trigger\" href=\"#modal1\"><i class=\"material-icons\">add</i></a>\n" +
                                    "</div>"

                    collectionElement.innerHTML = innerHtml

                    js("var elems = document.querySelectorAll('.modal');\n" +
                            "    console.log(elems);" +
                            "    var instances = M.Modal.init(elems, {});\n"
                    )

                    js("var elems = document.querySelectorAll('.dropdown-trigger');\n" +
                            "    var instances = M.Dropdown.init(elems, {});")


                    val exportiOSElement = document.getElementById("export_ios")
                    val exportAndroidElement = document.getElementById("export_android")
                    val exportWebElement = document.getElementById("export_web")

                    exportiOSElement?.addEventListener("click", fun(event: Event) {
                        saveiOS(projectJson)
                        console.log("saveiOS")
                    })

                    exportAndroidElement?.addEventListener("click", fun(event: Event) {
                        saveAndroid(projectJson)
                        console.log("saveAndroid")
                    })

                    exportWebElement?.addEventListener("click", fun(event: Event) {
                        saveWeb(projectJson)
                        console.log("saveWeb")
                    })
                }
            }
        }
    }
}

fun getColumNames(languages: ArrayList<String>): String {
    var str =  "<th>N</th>" + "<th>Screen</th>" +  "<th>Key</th>"
    for (language in languages) {
        str += "<th>${language}</th>"
    }
    return str
}

fun addLanguageInputsToPopup(json: Json): Unit {
    val element = document.getElementById("localization_input")
    if (element != null) {
        var innerHtml = ""


        val languagesJson = json["languages"] as Json
        js("Object").values(languagesJson).forEach(fun (language: dynamic) {
            var languageName = language["langName"] as String
            var languageCode = language["langCode"] as String

            innerHtml += "" +
                    "<div class=\"row\">" +
                    "   <div class=\"input-field col s12\">" +
                    "   <input id=\"language_input\" data-key=\"${languageCode}\" type=\"text\" class=\"validate\">" +
                    "   <label for=\"language_input\">${languageName}</label>" +
                    "   </div>" +
                    "</div>"

        })

        element.innerHTML = innerHtml
    }
}

fun getRows(json: Json): String {
    var str = ""
    var index = 0

    var screens = arrayListOf<String>()
    val screensJson = json["screens"] as Json
    js("Object").values(screensJson).forEach(fun(screen: String) {
        screens.add(screen)
    })

    val localization = json["localization"] as Json
    for (screen in screens) {

        val screenLocalization = localization[screen] as? Json
        if (screenLocalization != null) {
            js("Object").values(screenLocalization).forEach(fun(localization: dynamic) {
                index++
                val key = localization["key"] as String

                str += "<tr>" +
                        "<td>${index}</td>" +
                        "<td>${screen}</td>" +
                        "<td>${key}</td>"

                var values = arrayListOf<String>()
                val valuesJson = localization["values"] as Json
                js("Object").values(valuesJson).forEach(fun(value: dynamic) {
                    val languageKey = value["lang_key"] as String
                    val languageValue = value["lang_value"] as String
                    str += "<td>${languageValue}</td>"
                })

                str += "</tr>"
            })

        } else {
            continue
        }
    }

    return str
}

/// Helpers

@Deprecated("As we use Yandex translate, so we need to support same languages like Yandex, use YandexHelper.supportedLanguages() instead", ReplaceWith("YandexHelper.supportedLanguages()"), DeprecationLevel.WARNING)
fun loadJSON(callBack: (HashMap<String, String>) -> Unit) {
    val json = js("langs")
    val map = hashMapOf<String, String>()
    js("Object").keys(json).forEach(fun (key: String) {
        map[key] = json[key] as String
    })
    callBack(map)
}

external fun alert(message: Any?): Unit
external fun encodeURIComponent(uri: String): String
external fun initScreenAutocompleteList(screenNames: Array<String>): Unit
external fun initTypeAutocompleteList(types: Array<String>): Unit
external fun encodeURI(uri: String): String
external fun saveiOS(project: Json): Unit
external fun saveAndroid(project: Json): Unit
external fun saveWeb(project: Json): Unit