import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { Franc, FrancLanguage } from './franc';
import { Language } from 'app/entities/tutor-group.model';

@Injectable({ providedIn: 'root' })
export class TextEditorService {
    readonly franc: Franc = require('franc-min');

    constructor(private http: HttpClient) {}

    /**
     * Gets the data needed for the text editor, which includes the participation, textSubmission with
     * answer if existing and the assessments if the submission was already submitted.
     * @param id the id of the participation for which to find the data for the text editor
     */
    get(id: number): Observable<any> {
        return this.http.get(`api/text-editor/${id}`, { responseType: 'json' });
    }

    /**
     * Takes a text and returns it's language
     * @param   {String} text
     *
     * @returns {Language} language of the text
     */
    predictLanguage(text: string): Language | null {
        const languageProbabilities = this.franc.all(text);

        switch (languageProbabilities[0][0]) {
            case FrancLanguage.ENGLISH:
                return Language.ENGLISH;

            case FrancLanguage.GERMAN:
                return Language.GERMAN;

            case FrancLanguage.UNDEFINED:
            default:
                return null;
        }
    }
}
