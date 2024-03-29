package alumno

class ImportDataService {

    def onlineCityList = [1:"", 2:"MS", 3:"BC", 4:"CM", 5:"CO", 6:"CAP", 7:"FE", 8:"GL", 9:"HC", 10:"LH", 11:"LA",
            12:"MP", 13:"NW", 14:"RD", 15:"RN", 16:"SA", 17:"SJ", 18:"SN", 19:"SC", 20:"SR", 21:"SV", 22:"SQ", 23:"SU"]

    def highschoolCityList = ["Los Gatos":"", "Monte Sereno":"MS", "Boulder Creek":"BC", "Campbell":"CM", "Corralitos":"CO",
            "Capitola":"CAP", "Felton":"FE", "Gilroy":"GL", "Holy City":"HC", "La Honda":"LH", "los Altos":"LA",
            "Menlo Park":"MP", "Newark":"NW", "Redwood Estates":"RD", "Reno":"RN", "Salinas":"SA", "San Jose":"SJ",
            "Santa Clara":"SN", "Santa Cruz":"SC", "Saratoga":"SR", "Scotts Valley":"SV", "Soquel":"SQ", "Sunnyvale":"SU",
            "Ben Lomond":"BL"]

    def importHighSchoolData() {
        StudentImportHighSchoolXLS importer = new StudentImportHighSchoolXLS('lghsStudents.xlsx')
        def studentsMapList = importer.getStudents()
        for (it in studentsMapList) {
            if (!it.denied) {
                def matchingExistingStudent = Student.findByLastNameIlikeAndFirstNameIlike(it.lastName, it.firstName)
                if (!matchingExistingStudent){
                    Registration theReg = new Registration()
                    theReg.setIpAddress(-1)
                    theReg.save()
                    Student theStudent = new Student()
                    theStudent.setLastName(it.lastName)
                    theStudent.setFirstName(it.firstName)
                    theStudent.setGrade(it.grade.toInteger())
                    theReg.addToStudents(theStudent)
                    theStudent.save()
                    Household theHousehold = new Household()
                    theHousehold.setAddress(it.householdAddress)
                    def cityAbbreviation = highschoolCityList[(it.city?: "Los Gatos")]
                    theHousehold.setCity(cityAbbreviation)
                    theHousehold.setZip(it.zip)
                    theHousehold.setState('CA')
                    theHousehold.setSafehome(false)
                    def householdPhone = convertToStandardPhoneFormat(it.homePhone)
                    theHousehold.setPhoneNumber(strip408AreaCode(householdPhone))
                    theReg.addToHouseholds(theHousehold)
                    theHousehold.save()
                    def parentStrings = splitParentNameCell(it.parentNames, "&");
                    parentStrings.reverseEach { parentString ->
                        Parent theParent = new Parent()
                        def firstAndLastNames = getParentNamesFromString(parentString, it.lastName)
                        theParent.setFirstName(firstAndLastNames[0])
                        if (firstAndLastNames[1])
                            theParent.setLastName(firstAndLastNames[1])
                        else
                            theParent.setLastName(theHousehold.getParents()[0]?.getLastName())  //bit of a hack but there are only two parents
                        theHousehold.addToParents(theParent)
                        theParent.save()
                    }
                    theHousehold.save()
                    theReg.save()
                }
            }
        }
    }

    def splitParentNameCell(String sentence, String splitPattern) {
        def tokens

        if (sentence) {
            tokens = sentence.split(splitPattern)
            // remove leading and trailing whitespace from strings
            tokens.eachWithIndex { token, index ->
                tokens[index] = token.trim()
            }
        }

        return tokens
    }

    public String[] getParentNamesFromString(String parentName, String childsLastName){
        // Case 1: Tom
        // Case 2: Tom van't Hof - for this case need to check if van't is middle name
        //                          or matches child's last name "van't Hof"
        // Case 3: Tom Smith

        def tokens = parentName.split("\\s+")

        String lastName = null
        String firstName = null
        if (tokens.length > 1){
            String possibleParentLastName = tokens[tokens.length - 2] + " " + tokens[tokens.length - 1]
            if (possibleParentLastName.startsWith(childsLastName)) {
                lastName = possibleParentLastName
                firstName = (parentName.substring(0, (parentName.length() - lastName.length()))).trim()
            }
            else {
                lastName = tokens[tokens.length - 1]
                firstName = (parentName.substring(0, (parentName.length() - lastName.length()))).trim()
            }
        }
        else    //Case where parent cell is "Tom & Susie Smith"   the one token returned for "Tom" is first name
        {
            firstName = tokens[0]
        }
        def parentNames = [firstName, lastName]
        return parentNames
    }



    def importOnlineData() {
        OnlineImportXLS importer = new OnlineImportXLS('wildcats.xlsx')
        def registrationsMapList = importer.getRegistrations()
        def studentsMapList = importer.getStudents()
        def householdsMapList = importer.getHouseholds()
        def parentsMapList = importer.getParents()

        registrationsMapList.each() {
            Registration theReg = new Registration()
            theReg.setIpAddress(it.ipAddress)
            theReg.setDateCreated(it.dateCreated)
            def regOnlineID = it.dbID
            theReg.save()
            def listOfStudentsForThisReg = studentsMapList.findAll {studentMap -> studentMap.registrationDbID==regOnlineID}
            if (listOfStudentsForThisReg.size() == 0) {
                theReg.delete()
            }
            else {
                listOfStudentsForThisReg.each() { studentMap ->
                    // Search to see if student already exists if so delete that old reg
                    // Decision is to use the latest reg for a student
                    def duplicateOldStudent = Student.findByLastNameIlikeAndFirstNameIlike(studentMap.lastName?.trim(), studentMap.firstName?.trim())
                    if (duplicateOldStudent?.grade == studentMap.grade) {
                        duplicateOldStudent.getRegistration().delete(flush: true)
                    }
                    Student theStudent = new Student()
                    def studentLastName = studentMap.lastName ?: " "
                    theStudent.setLastName(convertToFirstCaps(studentLastName?.trim()))
                    def studentFirstName = studentMap.firstName ?: " "
                    theStudent.setFirstName(convertToFirstCaps(studentFirstName?.trim(),true))
                    def studentGrade = studentMap.grade ?: 13.0
                    theStudent.setGrade(studentGrade.toInteger())
                    def studentPhone = convertToStandardPhoneFormat(studentMap.phoneNumber)
                    theStudent.setPhoneNumber(strip408AreaCode(studentPhone))
                    theStudent.setEmail(studentMap.email?.trim())
                    theReg.addToStudents(theStudent)
                    theStudent.save()
                }
                def listOfHouseholdsForThisReg = householdsMapList.findAll {householdMap -> householdMap.registrationDbID==regOnlineID}
                listOfHouseholdsForThisReg.each() { householdMap ->
                    Household theHousehold = new Household()
                    theHousehold.setAddress(convertToFirstCaps(householdMap.address?.trim()))
                    def cityAbbreviation = onlineCityList[Integer.valueOf(householdMap.city?.toInteger()?: 1)]
                    theHousehold.setCity(cityAbbreviation)
                    theHousehold.setZip(householdMap.zip?: "95030")
                    theHousehold.setState('CA')
                    theHousehold.setSafehome(householdMap.safeHouse!=0.0)
                    def phoneNumber = convertToStandardPhoneFormat(householdMap.phoneNumber)
                    theHousehold.setPhoneNumber(strip408AreaCode(phoneNumber))
                    theReg.addToHouseholds(theHousehold)
                    theHousehold.save()
                    def householdOnlineID = householdMap.dbID
                    def listOfParentsForThisHousehold= parentsMapList.findAll {parentMap -> parentMap.householdDbID==householdOnlineID}
                    listOfParentsForThisHousehold.each() { parentMap ->
                        // first check to see if first name of "parent" is actually compound name "Bob & Mary"
                        def parentStrings = splitParentNameCell(parentMap.firstName, "&")
                        if (parentStrings?.length < 2)
                            parentStrings = splitParentNameCell(parentMap.firstName, " and ")
                        Parent theParent = new Parent()
                        theParent.setLastName(convertToFirstCaps(parentMap.lastName?.trim()))
                        if (parentStrings)
                            theParent.setFirstName(convertToFirstCaps(parentStrings[0],true))
                        else
                            theParent.setFirstName(null)
                        def parentPhoneNumber = convertToStandardPhoneFormat(parentMap.phoneNumber)
                        theParent.setPhoneNumber(strip408AreaCode(parentPhoneNumber))
                        theParent.setEmail(parentMap.email?.trim())
                        theHousehold.addToParents(theParent)
                        theParent.save()
                        if (parentStrings?.length == 2) {
                            // add second parent that wasn't explicitly entered
                            Parent the2ndParent = new Parent()
                            the2ndParent.setLastName(convertToFirstCaps(parentMap.lastName?.trim()))
                            the2ndParent.setFirstName(convertToFirstCaps(parentStrings[1]))
                            theHousehold.addToParents(the2ndParent)
                            the2ndParent.save()
                        }
                     }
                    theHousehold.save()
                }
                theReg.save()
            }
        }
    }

    def convertToFirstCaps (string, checkLength=false) {
        if (string) {
            if ((string.toUpperCase().equals(string) || string.toLowerCase().equals(string))) {
                if (checkLength && string.length() < 3) {
                    return string
                }
                else {
                    final StringBuilder result = new StringBuilder(string.length())
                    String[] words = string.split("\\s")
                    words.eachWithIndex {word, index ->
                        if (index > 0)
                            result.append(" ")
                        result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase())
                    }
                    return result.toString()
                }
            }
        }
        return string
    }



    def convertToStandardPhoneFormat(String inputNumber) {
         // phone numbers could be multiple formats
        // First step is to strip out all non numeric characters
        // if remaining string is 7 characters long format without area code otherwise include it

        String number = inputNumber?.replaceAll("[^0-9]", "")
        if (number?.length() < 7)
            return null //bad entry in the front end
        if (number.length() == 10 )
            return String.format("(%s) %s-%s", number.substring(0, 3), number.substring(3, 6), number.substring(6, 10))
        else
            return String.format("%s-%s", number.substring(0,3), number.substring(3,7))
 }

    public String strip408AreaCode(String numberString) {
            if (numberString?.startsWith("(408)"))
                return numberString.substring(6)
            else
                return numberString;
    }

}
