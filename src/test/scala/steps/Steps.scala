package steps

import java.util.UUID

import com.typesafe.config.ConfigFactory
import cucumber.api.scala.{EN, ScalaDsl}
import helpers.graphql.GraphqlUtility
import helpers.steps.StepsUtility
import helpers.users.{KeycloakClient, RandomUtility, UserCredentials}
import org.junit.Assert
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.{By, JavascriptExecutor, WebDriver}
import org.scalatest.Matchers
import org.openqa.selenium.support.ui.{ExpectedConditions, Select, WebDriverWait}

import scala.jdk.CollectionConverters._

class Steps extends ScalaDsl with EN with Matchers {
  var webDriver: WebDriver = _
  var userId: String = ""
  var consignmentId: UUID = _

  val configuration = ConfigFactory.load()
  val baseUrl: String = configuration.getString("tdr.base.url")
  val authUrl: String = configuration.getString("tdr.auth.url")
  val userName: String = RandomUtility.randomString()
  val password: String = RandomUtility.randomString(10)
  val nonTdrPageUrl: String = configuration.getString("redirect.base.url")
  val userCredentials: UserCredentials = UserCredentials(userName, password)

  Before() { scenario =>
    webDriver = new ChromeDriver(StepsUtility.getChromeOptions)
  }

  After() { scenario =>
    webDriver.quit()
    KeycloakClient.deleteUser(userId)
  }

  private def login = {
    webDriver.get(s"$baseUrl")
    val startElement = webDriver.findElement(By.cssSelector(".govuk-button--start"))
    startElement.click()
    StepsUtility.userLogin(webDriver, userCredentials)
  }

  Given("^A logged out user") {
    userId = KeycloakClient.createUser(userCredentials)
  }

  Given("^A logged in user who is a member of (.*) transferring body") {
    body: String =>
      userId = KeycloakClient.createUser(userCredentials, Some(body))
      login
  }

  Given("^A logged in user who is not a member of a transferring body") {
    userId = KeycloakClient.createUser(userCredentials, Option.empty)
    login
  }

  Given("^A logged in user") {
    userId = KeycloakClient.createUser(userCredentials)
    login
  }

  And("^the user is logged in on the (.*) page") {
    page: String =>
      loadPage(page)
      StepsUtility.userLogin(webDriver, userCredentials)
  }

  Given("^an existing user") {
    userId = KeycloakClient.createUser(userCredentials)
  }

  When("^the user navigates to TDR Home Page") {
    webDriver.get(s"$baseUrl")
  }

  Then("^the logged out user enters valid credentials") {
    StepsUtility.enterUserCredentials(webDriver, userCredentials)
  }

  And("^the logged out user enters invalid credentials") {
    val userNameElement = webDriver.findElement(By.cssSelector("[name='username']"))
    val passwordElement = webDriver.findElement(By.cssSelector("[name='password']"))

    userNameElement.sendKeys("dgfhfdgjhgfj")
    passwordElement.sendKeys("fdghfdgh")
  }

  When("^the logged in user navigates to the (.*) page") {
    page: String =>
      loadPage(page)
  }

  And("^the (.*) page is loaded") {
    page: String =>
      loadPage(page)
  }

  And("^the user clicks the (.*) element$") {
    selector: String =>
      val clickableElement = webDriver.findElement(By.cssSelector(selector))
      clickableElement.click()
  }

  Then("^the logged out user should be at the (.*) page") {
    page: String =>
      val currentUrl: String = webDriver.getCurrentUrl

      Assert.assertTrue(currentUrl.startsWith(s"$authUrl/$page"))
  }

  Then("^the user should be at the (.*) page") {
    page: String =>
      val currentUrl: String = webDriver.getCurrentUrl

      Assert.assertTrue(s"actual: $currentUrl, expected: $page", currentUrl.startsWith(s"$baseUrl/$page") || currentUrl.endsWith(page))
  }

  Then("^the user will remain on the (.*) page") {
    page: String =>
      val currentUrl: String = webDriver.getCurrentUrl
      Assert.assertTrue(currentUrl.startsWith(s"$authUrl/$page"))
  }

  Then("^the user should see a user specific general error (.*)") {
    errorMessage: String =>
      val errorElement = webDriver.findElement(By.cssSelector("#general-error"))
      Assert.assertNotNull(errorElement)
      val specificError = errorMessage.replace("{userId}", s"Some($userId)")

      Assert.assertTrue(errorElement.getText.contains(specificError))
  }

  And("^the user will see the error message (.*)") {
    errorMessage: String =>
      val errorElement = webDriver.findElement(By.cssSelector("#error-details"))
      Assert.assertNotNull(errorElement)
      Assert.assertEquals(errorMessage, errorElement.getText)
  }

  And("^the user will see a form error message (.*)") {
    formErrorMessage: String =>
      val errorElement = webDriver.findElement(By.cssSelector(".govuk-error-message"))
      Assert.assertNotNull(errorElement)
      Assert.assertEquals(s"Error:\n" + formErrorMessage, errorElement.getText)
  }

  Then("^the user should see the (.*) dropdown values (.*)") {
    (name: String, expectedValues: String) =>
      val seriesList: List[String] = expectedValues.split(",").toList
      val seriesDropdown = new Select(webDriver.findElement(By.name(name)))
      val seriesText: List[String] = seriesDropdown.getOptions.asScala.map(_.getText).toList.tail

      Assert.assertEquals(seriesList, seriesText)
  }

  Then("^the user should see an empty (.*) dropdown") {
    name: String =>
      val seriesDropdown = new Select(webDriver.findElement(By.name(name)))
      val seriesText: List[String] = seriesDropdown.getOptions.asScala.map(_.getText).toList.tail
      Assert.assertTrue(seriesText.isEmpty)
  }

  Then("^the logged in user should stay at the (.*) page") {
    page: String =>
      val currentUrl: String = webDriver.getCurrentUrl
      Assert.assertTrue(currentUrl.startsWith(s"$baseUrl/$page"))
  }

  And("^the logged in user selects the series (.*)") {
    selectedSeries: String =>
      val seriesDropdown = new Select(webDriver.findElement(By.name("series")))
      seriesDropdown.selectByVisibleText(selectedSeries)
  }

  And("^the user clicks the continue button") {
    val button = webDriver.findElement(By.cssSelector("[type='submit']"))
    button.click()
  }

  And("^an existing consignment for transferring body (.*)") {
    body: String =>
      val client = GraphqlUtility(userCredentials)
      consignmentId = client.createConsignment(body).get.addConsignment.consignmentid.get
  }

  And("^an existing transfer agreement") {
    val client = GraphqlUtility(userCredentials)
    client.createTransferAgreement(consignmentId)
  }

  When("^the user uploads a file") {
    new WebDriverWait(webDriver, 10).until((driver: WebDriver) => {
      val executor = driver.asInstanceOf[JavascriptExecutor]
      executor.executeScript("return AWS.config && AWS.config.credentials && AWS.config.credentials.accessKeyId") != null
    })

    val input = webDriver.findElement(By.cssSelector("#file-selection"))
    input.sendKeys(s"${System.getProperty("user.dir")}/src/test/resources/testfiles")
    webDriver.findElement(By.cssSelector(".govuk-button")).click()
  }

  Then("^the (.*) should (.*) visible") {
    (element: String, visible: String) =>
      val id = element.replaceAll(" ", "-")
      new WebDriverWait(webDriver, 10).until((driver: WebDriver) => {
        val element = driver.findElement(By.cssSelector(s"#$id"))
        val shouldBeVisible = visible.equals("be")
        val isVisible = !element.getAttribute("class").contains("hide")
        shouldBeVisible || !isVisible
      })
  }

  And("^the page will redirect to the (.*) page after upload is complete") {
    page: String =>
      val _ = new WebDriverWait(webDriver, 10).until(ExpectedConditions.titleContains(page.capitalize))
  }

  private def loadPage(page: String) = {
    val pageWithConsignment = page match {
      case "series" => s"$baseUrl/$page"
      case _ => s"$baseUrl/consignment/$consignmentId/${page.toLowerCase.replaceAll(" ", "-")}"
    }
    webDriver.get(pageWithConsignment)
  }
}
